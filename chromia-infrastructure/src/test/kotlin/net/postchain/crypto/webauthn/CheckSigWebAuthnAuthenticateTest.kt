package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CheckSigWebAuthnAuthenticateTest {

    /**
     * https://w3c.github.io/webauthn/#sctn-test-vectors-none-es256
     */
    val authenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()
    val clientDataJSON = String("7b2274797065223a22776562617574686e2e676574222c226368616c6c656e6765223a224f63446e55685158756c5455506f334a5558543049393770767a7a59425039745a63685879617630314167222c226f726967696e223a2268747470733a2f2f6578616d706c652e6f7267222c2263726f73734f726967696e223a66616c73657d".hexStringToByteArray())
    val signature = "3046022100f50a4e2e4409249c4a853ba361282f09841df4dd4547a13a87780218deffcd380221008480ac0f0b93538174f575bf11a1dd5d78c6e486013f937295ea13653e331e87".hexStringToByteArray()
    val publicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

    @Test
    fun `should success with valid signature`() {
        assertDoesNotThrow { checkSignature(authenticatorData, clientDataJSON, publicKey, signature) }
    }

    @Test
    fun `should throw UserMistake when signature is wrong`() {
        val wrongSignature = signature.clone()
        wrongSignature[20] = 17
        assertFailure {
            checkSignature(authenticatorData, clientDataJSON, publicKey, wrongSignature)
        }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
    }

    @Test
    fun `should throw UserMistake when provided with invalid signature`() {
        val invalidSignature = ByteArray(32)
        assertFailure {
            checkSignature(authenticatorData, clientDataJSON, publicKey, invalidSignature)
        }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
    }

    @Test
    fun `should throw UserMistake when provided with wrong public key`() {
        val wrongPublicKey = publicKey.clone()
        wrongPublicKey[30] = 17
        assertFailure {
            checkSignature(authenticatorData, clientDataJSON, wrongPublicKey, signature)
        }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
    }

    @Test
    fun `should throw UserMistake when provided with null public key`() {
        val nullPublicKey = ByteArray(publicKey.size)
        assertFailure {
            checkSignature(authenticatorData, clientDataJSON, nullPublicKey, signature)
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
    }

    @Test
    fun `should throw UserMistake when provided with invalid public key`() {
        val invalidPublicKey = ByteArray(32) { it.toByte() }
        assertFailure {
            checkSignature(authenticatorData, clientDataJSON, invalidPublicKey, signature)
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                GtvFactory.gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnAuthenticate(Unit, opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("need 4 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                GtvFactory.gtv("message"),
                GtvFactory.gtv("{}"),
                GtvFactory.gtv(byteArrayOf()),
                GtvFactory.gtv(byteArrayOf()),
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnAuthenticate(Unit, opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    private fun checkSignature(authenticatorData: ByteArray, clientDataJSON: String, publicKey: ByteArray, signature: ByteArray) {
        val args = arrayOf(
                GtvFactory.gtv(authenticatorData),
                GtvFactory.gtv(clientDataJSON),
                GtvFactory.gtv(publicKey),
                GtvFactory.gtv(signature)
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        CheckSigWebAuthnAuthenticate(Unit, opData).checkCorrectness()
    }
}