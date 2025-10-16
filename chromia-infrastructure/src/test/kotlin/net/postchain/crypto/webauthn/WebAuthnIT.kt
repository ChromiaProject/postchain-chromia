package net.postchain.crypto.webauthn

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.query
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(60, unit = TimeUnit.SECONDS)
class WebAuthnIT : ManagedModeTest() {

    val objectConverter = ObjectConverter()

    @Test
    fun `register and authenticate success`() {
        startManagedSystem(3, 0)
        val dappChain = deployDappChain()

        val userName = "j_r_r_tolkien"
        val bookName = "The Lord of the Rings"

        val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-7.json")!!
        val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
        val credentialId = registrationResponse!!.rawId!!
        val attestationObject = registrationResponse.response!!.attestationObject
        val registrationClientDataJSON = String(registrationResponse.response!!.clientDataJSON)
        val transports = registrationResponse.response!!.transports.map { it.value }

        enqueueTx(
                dappChain, makeTransaction(
                dappChain,
                GtxOp(WebAuthnRegister.OP_NAME, gtv(credentialId), gtv(attestationObject), gtv(registrationClientDataJSON), gtv(transports.map { gtv(it) })),
                GtxOp("register_user", gtv(userName)),
        ))
        buildBlock(dappChain, 0)

        for (node in getChainNodes(dappChain)) {
            assertThat(node.query(dappChain) { it.query("get_user", gtv(mapOf("user_name" to gtv(userName)))) }!!
                    .asDict()["credential_id"]!!.asByteArray())
                    .isContentEqualTo(credentialId)
        }

        val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
        val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
        assertThat(authenticationResponse!!.rawId!!).isContentEqualTo(credentialId)
        val authenticatorData = authenticationResponse.response!!.authenticatorData
        val authenticationClientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
        val signature = authenticationResponse.response!!.signature

        enqueueTx(
                dappChain, makeTransaction(
                dappChain,
                GtxOp(WebAuthnAuthenticate.OP_NAME, gtv(credentialId), gtv(authenticatorData), gtv(authenticationClientDataJSON), gtv(signature)),
                GtxOp("create_book", gtv(bookName)),
        ))
        buildBlock(dappChain, 1)

        for (node in getChainNodes(dappChain)) {
            assertThat(node.query(dappChain) { it.query("get_book", gtv(mapOf("name" to gtv(bookName)))) }!!
                    .asDict()["user_name"]!!.asString())
                    .isEqualTo(userName)
        }
    }

    fun deployDappChain(signers: Set<Int> = setOf(0, 1, 2)): Long {
        val dappGtvConfig = GtvMLParser.parseGtvML(javaClass.getResource("/infra-libs/webauthn_test.xml")!!.readText())

        return startNewBlockchain(
                signers,
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())
    }

    fun makeTransaction(chainId: Long, vararg ops: GtxOp): ByteArray {
        val builder = GtxBuilder(ChainUtil.ridOf(chainId), emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
        ops.forEach { builder.addOperation(it.opName, *it.args) }
        return builder
                .addNop()
                .finish().buildGtx().encode()
    }
}
