package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.core.MockEContext
import net.postchain.crypto.sha256Digest
import net.postchain.crypto.webauthn.WebAuthnGTXModuleFactory.Companion.createWebAuthnManager
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class WebAuthnBindChallengeTest {

    private val objectConverter = ObjectConverter()
    private val webAuthnManager = createWebAuthnManager(objectConverter, false).first
    private val brid = BlockchainRid.buildRepeat(1)
    private val calculator = makeMerkleHashCalculator(2)
    private val credentialId = ByteArray(32) { 7 }
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey = objectConverter.cborConverter.writeValueAsBytes(
            EC2COSEKey.create(keyPair.public as ECPublicKey, COSEAlgorithmIdentifier.ES256))

    private val repository: WebAuthnRepository = mock {
        on { fetchCredential(any(), eq(credentialId)) } doReturn CredentialData(
                id = credentialId.wrap(),
                aaguid = ByteArray(16).wrap(),
                publicKey = publicKey.wrap(),
                signCount = 0,
                transports = "internal",
                uvInitialized = false,
                backupEligible = false,
                backupState = false,
                suspiciousSignCountPresented = null,
                suspiciousSignCountStored = null,
        )
    }

    private val transfer = OpData("transfer", arrayOf(gtv("aa".hexStringToByteArray()), gtv(5), gtv("n1")))

    @Test
    fun `operation challenge matches the Rell library`() {
        // lib/webauthn/test/auth_test.rell: test_operation_challenge_vector
        val challenge = operationChallenge(brid, transfer.opName, transfer.args, calculator)
        assertThat(challenge.toHex().lowercase()).isEqualTo("e9929c568805e0c26b8cf7b771b1423b79e8a1c28d9d43d67e2512b359fa71d7")
    }

    @Test
    fun `bound challenge for the next operation is accepted`() {
        val challenge = operationChallenge(brid, transfer.opName, transfer.args, calculator)
        assertDoesNotThrow { authenticate(challenge, listOf(transfer), bind = true) }
    }

    @Test
    fun `challenge for other arguments is rejected`() {
        val other = OpData("transfer", arrayOf(gtv("aa".hexStringToByteArray()), gtv(500), gtv("n1")))
        val challenge = operationChallenge(brid, transfer.opName, transfer.args, calculator)
        assertFailure { authenticate(challenge, listOf(other), bind = true) }
                .isInstanceOf(UserMistake::class.java).messageContains("challenge is not bound to the next operation transfer")
    }

    @Test
    fun `challenge for another blockchain is rejected`() {
        val challenge = operationChallenge(BlockchainRid.buildRepeat(2), transfer.opName, transfer.args, calculator)
        assertFailure { authenticate(challenge, listOf(transfer), bind = true) }
                .isInstanceOf(UserMistake::class.java).messageContains("challenge is not bound")
    }

    @Test
    fun `binds to the operation right after, not a later one`() {
        val challenge = operationChallenge(brid, transfer.opName, transfer.args, calculator)
        val nop = OpData("nop", arrayOf(gtv(1)))
        assertFailure { authenticate(challenge, listOf(nop, transfer), bind = true) }
                .isInstanceOf(UserMistake::class.java).messageContains("challenge is not bound to the next operation nop")
    }

    @Test
    fun `authenticate as the last operation is rejected`() {
        val challenge = operationChallenge(brid, transfer.opName, transfer.args, calculator)
        assertFailure { authenticate(challenge, listOf(), bind = true) }
                .isInstanceOf(UserMistake::class.java).messageContains("must be followed by the operation it authorizes")
    }

    @Test
    fun `any unique challenge is accepted when the flag is off`() {
        assertDoesNotThrow { authenticate(ByteArray(32) { 3 }, listOf(), bind = false) }
    }

    private fun authenticate(challenge: ByteArray, following: List<OpData>, bind: Boolean) {
        val clientDataJSON = """{"type":"webauthn.get","challenge":"${Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)}","origin":"https://example.org","crossOrigin":false}"""
        // rpIdHash | flags (UP) | signCount
        val authenticatorData = sha256Digest("example.org".toByteArray()) + byteArrayOf(0x01) + byteArrayOf(0, 0, 0, 1)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(authenticatorData + sha256Digest(clientDataJSON.toByteArray()))
            sign()
        }
        val args: Array<Gtv> = arrayOf(gtv(credentialId), gtv(authenticatorData), gtv(clientDataJSON), gtv(signature))
        val operations = (listOf(OpData(WebAuthnAuthenticate.OP_NAME, args)) + following).toTypedArray()
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, brid, arrayOf(), operations)
        WebAuthnAuthenticate(WebAuthnConfig(
                listOf(Origin("https://example.org")),
                false,
                "example.org",
                userPresence = true,
                userVerification = false,
                objectConverter = objectConverter,
                strictWebAuthnManager = webAuthnManager,
                nonStrictWebAuthnManager = webAuthnManager,
                repository = repository,
                bindChallengeToOperation = bind,
                merkleHashCalculator = calculator,
        ), opData).checkCorrectness(MockEContext(0))
    }
}
