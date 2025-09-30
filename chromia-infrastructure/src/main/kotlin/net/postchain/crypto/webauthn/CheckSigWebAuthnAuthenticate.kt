package net.postchain.crypto.webauthn

import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.client.ClientDataType
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.core.TxEContext
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.ExtOpData
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * WebAuthn authenticate.
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API#authenticating_a_user
 */
class CheckSigWebAuthnAuthenticate(val conf: WebAuthnConfig, opData: ExtOpData) : GTXOperation(opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.checksig_webauthn_authenticate"

        val metadata = OperationMetadata(args = listOf(
                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/authenticatorData from AuthenticatorAssertionResponse
                ArgumentMetadata("authenticator_data", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAssertionResponse
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),

                // COSE Algorithm Identifier, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKeyAlgorithm
                ArgumentMetadata("alg", setOf(GtvType.INTEGER)),

                // SubjectPublicKeyInfo, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKey
                ArgumentMetadata("public_key", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/signature from AuthenticatorAssertionResponse
                ArgumentMetadata("signature", setOf(GtvType.BYTEARRAY)),
        ))
    }

    override fun apply(ctx: TxEContext) = true

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing() {
        webAuthnAuthenticate(data.args)
    }

    override fun checkCorrectness() {
        webAuthnAuthenticate(data.args)
    }

    private fun webAuthnAuthenticate(args: Array<out Gtv>) {
        if (args.size != 5) throw UserMistake("need 5 args, got ${args.size}")
        val authenticatorData = args[0].asByteArray()
        val clientDataJSON = args[1].asString()
        val alg = args[2].asInteger()
        val publicKey = args[3].asByteArray()
        val signature = args[4].asByteArray()

        webAuthnAuthenticate(authenticatorData, clientDataJSON, alg, publicKey, signature)
    }

    private fun webAuthnAuthenticate(authenticatorData: ByteArray, clientDataJSON: String, alg: Long, publicKey: ByteArray, signature: ByteArray) {
        val objectConverter = ObjectConverter()

        val clientData = try {
            objectConverter.jsonConverter.readValue(clientDataJSON, object : TypeReference<CollectedClientData>() {})
                    ?: throw UserMistake("invalid clientData")
        } catch (_: DataConversionException) {
            throw UserMistake("invalid clientData")
        }

        val authData = try {
            AuthenticatorDataConverter(objectConverter).convert<AuthenticationExtensionAuthenticatorOutput>(authenticatorData)
        } catch (_: DataConversionException) {
            throw UserMistake("invalid authenticatorData")
        }

        if (clientData.type != ClientDataType.WEBAUTHN_GET) {
            throw UserMistake("wrong clientData.type")
        }

        if (clientData.challenge.value.isEmpty()) {
            throw UserMistake("missing clientData.challenge")
        }

        if (conf.allowedOrigins.isNotEmpty()) {
            if (!conf.allowedOrigins.contains(clientData.origin)) {
                throw UserMistake("origin does not match")
            }
        }

        @Suppress("USELESS_ELVIS") // clientData.crossOrigin can be null even though it's declared with @NotNull
        if (!conf.allowCrossOrigin && (clientData.crossOrigin ?: false)) {
            throw UserMistake("crossOrigin is set but now allowed")
        }

        if (conf.allowedRelyingPartyIdentifiers.isNotEmpty()) {
            if (!conf.allowedRelyingPartyIdentifiers.any {
                        sha256Digest(it.toByteArray(Charsets.UTF_8)).contentEquals(authData.rpIdHash)
                    }) {
                throw UserMistake("relying party identifier does not match")
            }
        }

        if (conf.userPresence) {
            if (!authData.isFlagUP) {
                throw UserMistake("user presence is required, but user is not present")
            }
        }

        if (conf.userVerification) {
            if (!authData.isFlagUV) {
                throw UserMistake("user verification is required, but user is not verified")
            }
        }

        if (!authData.isFlagBE && authData.isFlagBS) {
            throw UserMistake("backup state bit must not be set if backup eligibility bit is not set")
        }

        val signedData = authenticatorData + sha256Digest(clientDataJSON.toByteArray(Charsets.UTF_8))

        val pk = try {
            val key = X509EncodedKeySpec(publicKey)
            val kFact = KeyFactory.getInstance(getKeyAlgorithm(alg))
            kFact.generatePublic(key)
        } catch (e: GeneralSecurityException) {
            throw UserMistake("invalid public key: ${e.message}")
        }

        try {
            val signatureChecker = Signature.getInstance(getSignatureAlgorithm(alg))
            signatureChecker.initVerify(pk)
            signatureChecker.update(signedData)
            if (!signatureChecker.verify(signature)) {
                throw UserMistake("signature verification failed")
            }
        } catch (e: GeneralSecurityException) {
            throw UserMistake("unable to verify signature: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw UserMistake("unable to verify signature: ${e.message}")
        }
    }

    private fun getKeyAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "EC"
        -8L -> "EdDSA"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }

    private fun getSignatureAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "SHA256withECDSA"
        -8L -> "ed25519"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }
}
