package net.postchain.crypto.webauthn

import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
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
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * WebAuthn register.
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API#creating_a_key_pair_and_registering_a_user
 */
class CheckSigWebAuthnRegister(conf: WebAuthnConfig, opData: ExtOpData) : WebAuthnOperation(conf, opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.webauthn_register"

        val metadata = OperationMetadata(args = listOf(
                // https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/rawId
                ArgumentMetadata("id", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/attestationObject
                ArgumentMetadata("attestation_object", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAttestationResponse
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),

                // COSE Algorithm Identifier, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKeyAlgorithm
                ArgumentMetadata("alg", setOf(GtvType.INTEGER)),

                // SubjectPublicKeyInfo, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKey
                ArgumentMetadata("public_key", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getTransports
                ArgumentMetadata("transports", setOf(GtvType.ARRAY)),
        ))
    }

    override fun apply(ctx: TxEContext) = true

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing() {
        webAuthnRegister(data.args)
    }

    override fun checkCorrectness() {
        webAuthnRegister(data.args)
    }

    private fun webAuthnRegister(args: Array<out Gtv>) {
        if (args.size != 6) throw UserMistake("need 6 args, got ${args.size}")
        val id = args[0].asByteArray()
        val attestationObject = args[1].asByteArray()
        val clientDataJSON = args[2].asString()
        val alg = args[3].asInteger()
        val publicKey = args[4].asByteArray()
        val transports = args[5].asArray().map { it.asString() }

        webAuthnRegister(id, attestationObject, clientDataJSON, alg, publicKey, transports)
    }

    private fun webAuthnRegister(id: ByteArray, attestationObject: ByteArray, clientDataJSON: String, alg: Long, publicKey: ByteArray, transports: List<String>) {
        val objectConverter = ObjectConverter()

        val clientData = try {
            objectConverter.jsonConverter.readValue(clientDataJSON, object : TypeReference<CollectedClientData>() {})
                    ?: throw UserMistake("invalid clientData")
        } catch (_: DataConversionException) {
            throw UserMistake("invalid clientData")
        }

        if (clientData.type != ClientDataType.WEBAUTHN_CREATE) {
            throw UserMistake("wrong clientData.type")
        }

        verifyChallenge(clientData)

        verifyOrigin(clientData)

        val attObject = try {
            AttestationObjectConverter(objectConverter).convert(attestationObject)
                    ?: throw UserMistake("invalid attestationObject")
        } catch (_: DataConversionException) {
            throw UserMistake("invalid attestationObject")
        }
        val authData = attObject.authenticatorData

        verifyRpId(authData)

        verifyUser(authData)

        verifyBackup(authData)

        val (coseKey, credentialId) = authData.attestedCredentialData?.let {
            it.coseKey to it.credentialId
        } ?: throw UserMistake("invalid attestationObject")

        parsePublicKey(alg, publicKey) // validate it
        if (coseKey.algorithm?.value != alg) {
            throw UserMistake("alg mismatch")
        }
        if (!coseKey.publicKey?.encoded.contentEquals(publicKey)) {
            throw UserMistake("public key mismatch")
        }

        if (credentialId.isEmpty()) {
            throw UserMistake("empty credentialId")
        }
        if (credentialId.size > 1023) {
            throw UserMistake("credentialId too long")
        }
        if (!credentialId.contentEquals(id)) {
            throw UserMistake("credentialId mismatch")
        }
    }
}

/**
 * WebAuthn authenticate.
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API#authenticating_a_user
 */
class CheckSigWebAuthnAuthenticate(conf: WebAuthnConfig, opData: ExtOpData) : WebAuthnOperation(conf, opData) {
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

        verifyChallenge(clientData)

        verifyOrigin(clientData)

        verifyRpId(authData)

        verifyUser(authData)

        verifyBackup(authData)

        val signedData = authenticatorData + sha256Digest(clientDataJSON.toByteArray(Charsets.UTF_8))

        try {
            val signatureChecker = Signature.getInstance(getSignatureAlgorithm(alg))
            signatureChecker.initVerify(parsePublicKey(alg, publicKey))
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
}

abstract class WebAuthnOperation(val conf: WebAuthnConfig, opData: ExtOpData) : GTXOperation(opData) {
    fun verifyChallenge(clientData: CollectedClientData) {
        if (clientData.challenge.value.isEmpty()) {
            throw UserMistake("missing clientData.challenge")
        }
    }

    fun verifyOrigin(clientData: CollectedClientData) {
        if (conf.allowedOrigins.isNotEmpty()) {
            if (!conf.allowedOrigins.contains(clientData.origin)) {
                throw UserMistake("origin does not match")
            }
        }

        @Suppress("USELESS_ELVIS") // clientData.crossOrigin can be null even though it's declared with @NotNull
        if (!conf.allowCrossOrigin && (clientData.crossOrigin ?: false)) {
            throw UserMistake("crossOrigin is set but now allowed")
        }
    }

    fun verifyRpId(authData: AuthenticatorData<*>) {
        if (conf.relyingPartyIdentifier.isNotEmpty()) { // TODO WebAuthn: require this to be set and always verify it
            if (!sha256Digest(conf.relyingPartyIdentifier.toByteArray(Charsets.UTF_8)).contentEquals(authData.rpIdHash)) {
                throw UserMistake("relying party identifier does not match")
            }
        }
    }

    fun verifyBackup(authData: AuthenticatorData<*>) {
        if (!authData.isFlagBE && authData.isFlagBS) {
            throw UserMistake("backup state bit must not be set if backup eligibility bit is not set")
        }
    }

    fun verifyUser(authData: AuthenticatorData<*>) {
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
    }


    fun parsePublicKey(alg: Long, publicKey: ByteArray): PublicKey = try {
        val key = X509EncodedKeySpec(publicKey)
        val kFact = KeyFactory.getInstance(getKeyAlgorithm(alg))
        kFact.generatePublic(key)
    } catch (e: GeneralSecurityException) {
        throw UserMistake("invalid public key: ${e.message}")
    }

    fun getKeyAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "EC"
        -8L -> "EdDSA"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }

    fun getSignatureAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "SHA256withECDSA"
        -8L -> "ed25519"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }
}
