package net.postchain.crypto.webauthn

import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.ClientDataType
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.OriginVerifierImpl
import com.webauthn4j.verifier.exception.ConstraintViolationException
import com.webauthn4j.verifier.exception.InconsistentClientDataTypeException
import com.webauthn4j.verifier.exception.VerificationException
import com.webauthn4j.verifier.internal.BEBSFlagsVerifier
import com.webauthn4j.verifier.internal.BeanAssertUtil
import com.webauthn4j.verifier.internal.ChallengeVerifier
import com.webauthn4j.verifier.internal.CrossOriginFlagVerifier
import com.webauthn4j.verifier.internal.RpIdHashVerifier
import com.webauthn4j.verifier.internal.UPUVFlagsVerifier
import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.core.EContext
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
class WebAuthnRegister(conf: WebAuthnConfig, opData: ExtOpData) : WebAuthnOperation(conf, opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.webauthn_register"

        val metadata = OperationMetadata(args = listOf(
                // https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/rawId
                ArgumentMetadata("id", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/attestationObject
                ArgumentMetadata("attestation_object", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAttestationResponse
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getTransports
                ArgumentMetadata("transports", setOf(GtvType.ARRAY)),
        ))
    }

    private var credential: CredentialData? = null

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) {
        webAuthnRegister(ctxt, data.args)
    }

    override fun checkCorrectness(ctxt: EContext) {
        webAuthnRegister(ctxt, data.args)
    }

    private fun webAuthnRegister(ctxt: EContext, args: Array<out Gtv>) {
        if (args.size != 4) throw UserMistake("need 4 args, got ${args.size}")
        val id = args[0].asByteArray()
        val attestationObject = args[1].asByteArray()
        val clientDataJSON = args[2].asString()
        val transports = args[3].asArray().map { it.asString() }

        webAuthnRegister(ctxt, id, attestationObject, clientDataJSON, transports)
    }

    private fun webAuthnRegister(ctxt: EContext, id: ByteArray, attestationObjectBytes: ByteArray, clientDataJSON: String, transports: List<String>) {
        if (id.isEmpty()) {
            throw UserMistake("empty credentialId")
        }
        if (id.size > 1023) {
            throw UserMistake("credentialId too long")
        }

        val registrationRequest = RegistrationRequest(
                attestationObjectBytes,
                clientDataJSON.toByteArray(Charsets.UTF_8),
                /*clientExtensionsJSON=*/null,
                transports.toSet(),
        )
        val registrationData = try {
            conf.webAuthnManager.parse(registrationRequest)
        } catch (e: DataConversionException) {
            throw UserMistake(e.message ?: "verification failed")
        }
        val collectedClientData = registrationData.collectedClientData ?: throw UserMistake("invalid client data")

        verifyChallenge(collectedClientData)

        val serverProperty = ServerProperty(conf.allowedOrigins.toSet(), conf.relyingPartyIdentifier, collectedClientData.challenge)
        val registrationParameters = RegistrationParameters(
                serverProperty,
                listOf(
                        PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
                        PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.EdDSA)
                ),
                conf.userVerification,
                conf.userPresence,
        )
        val verifiedRegistrationData = try {
            // TODO WebAuthn: Remove this when https://github.com/webauthn4j/webauthn4j/issues/1170 is fixed
            CrossOriginFlagVerifier.verify(collectedClientData, conf.allowCrossOrigin)

            conf.webAuthnManager.verify(registrationData, registrationParameters)
        } catch (e: VerificationException) {
            throw UserMistake(e.message ?: "verification failed")
        }

        val attestationObject = verifiedRegistrationData.attestationObject
                ?: throw UserMistake("invalid attestationObject")
        val (coseKey, credentialId) = attestationObject.authenticatorData.attestedCredentialData?.let {
            it.coseKey to it.credentialId
        } ?: throw UserMistake("invalid attestedCredentialData")
        val alg = coseKey.algorithm?.value ?: throw UserMistake("invalid attestedCredentialData: no alg")
        val publicKey = coseKey.publicKey?.encoded ?: throw UserMistake("invalid attestedCredentialData: no public key")

        parsePublicKey(alg, publicKey) // validate it

        if (!credentialId.contentEquals(id)) {
            throw UserMistake("credentialId mismatch")
        }

        if (verifiedRegistrationData.transports != transports.map { AuthenticatorTransport.create(it) }.toSet()) {
            throw UserMistake("transports mismatch")
        }

        credential = CredentialData(
                id = id.wrap(),
                alg = alg,
                publicKey = publicKey.wrap(),
                signCount = attestationObject.authenticatorData.signCount,
                transports = transports.joinToString(separator = ","),
                uvInitialized = attestationObject.authenticatorData.isFlagUV,
                backupEligible = attestationObject.authenticatorData.isFlagBE,
                backupState = attestationObject.authenticatorData.isFlagBS,
        )
    }

    override fun apply(ctx: TxEContext): Boolean = credential?.let {
        conf.repository.persistCredential(ctx, data.opIndex, it)
        true
    } ?: false
}

/**
 * WebAuthn authenticate.
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API#authenticating_a_user
 */
class WebAuthnAuthenticate(conf: WebAuthnConfig, opData: ExtOpData) : WebAuthnOperation(conf, opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.webauthn_authenticate"

        val metadata = OperationMetadata(args = listOf(
                // https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/rawId
                ArgumentMetadata("id", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/authenticatorData from AuthenticatorAssertionResponse
                ArgumentMetadata("authenticator_data", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAssertionResponse
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/signature from AuthenticatorAssertionResponse
                ArgumentMetadata("signature", setOf(GtvType.BYTEARRAY)),
        ))
    }

    private var credential: CredentialData? = null

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) {
        webAuthnAuthenticate(ctxt, data.args)
    }

    override fun checkCorrectness(ctxt: EContext) {
        webAuthnAuthenticate(ctxt, data.args)
    }

    private fun webAuthnAuthenticate(ctxt: EContext, args: Array<out Gtv>) {
        if (args.size != 4) throw UserMistake("need 4 args, got ${args.size}")
        val id = args[0].asByteArray()
        val authenticatorData = args[1].asByteArray()
        val clientDataJSON = args[2].asString()
        val signature = args[3].asByteArray()

        webAuthnAuthenticate(ctxt, id, authenticatorData, clientDataJSON, signature)
    }

    private fun webAuthnAuthenticate(ctxt: EContext, id: ByteArray, authenticatorDataBytes: ByteArray, clientDataJSON: String, signature: ByteArray) {
        if (id.isEmpty()) {
            throw UserMistake("empty credentialId")
        }
        if (id.size > 1023) {
            throw UserMistake("credentialId too long")
        }

        val authenticationRequest = AuthenticationRequest(
                /*credentialId=*/id,
                /*userHandle=*/null,
                authenticatorDataBytes,
                clientDataJSON.toByteArray(Charsets.UTF_8),
                /*clientExtensionsJSON=*/null,
                signature,
        )

        val authenticationData: AuthenticationData = try {
            conf.webAuthnManager.parse(authenticationRequest)
        } catch (e: DataConversionException) {
            throw UserMistake(e.message ?: "verification failed")
        }
        val collectedClientData = authenticationData.collectedClientData ?: throw UserMistake("invalid client data")
        val authenticatorData = authenticationData.authenticatorData ?: throw UserMistake("invalid authenticator data")

        verifyChallenge(collectedClientData)

        val serverProperty = ServerProperty(conf.allowedOrigins.toSet(), conf.relyingPartyIdentifier, collectedClientData.challenge)

        val credential = conf.repository.fetchCredential(ctxt, id)
                ?: throw UserMistake("credential with id ${id.toHex()} not registered")

        try {
            verifyAuthentication(authenticationData, serverProperty)
        } catch (e: VerificationException) {
            throw UserMistake(e.message ?: "verification failed")
        }

        verifySignature(authenticatorDataBytes, clientDataJSON, credential.alg, credential.publicKey.data, signature)

        this.credential = credential.copy(signCount = authenticatorData.signCount, backupState = authenticatorData.isFlagBS)
    }

    override fun apply(ctx: TxEContext): Boolean = credential?.let {
        conf.repository.updateCredential(ctx, it.id.data, it.signCount, it.backupState)
        true
    } ?: false

    // Copied from https://github.com/webauthn4j/webauthn4j/blob/master/webauthn4j-core/src/main/java/com/webauthn4j/verifier/AuthenticationDataVerifier.java#L64
    // and translated to Kotlin and modified
    private fun verifyAuthentication(authenticationData: AuthenticationData, serverProperty: ServerProperty) {
        // BeanAssertUtil.validate(authenticationData)

        val collectedClientData = authenticationData.collectedClientData!!
        val authenticatorData = authenticationData.authenticatorData!!

        BeanAssertUtil.validate(collectedClientData)
        BeanAssertUtil.validate(authenticatorData)

        if (authenticatorData.attestedCredentialData != null) {
            throw ConstraintViolationException("attestedCredentialData must be null on authentication")
        }

        if (collectedClientData.type != ClientDataType.WEBAUTHN_GET) {
            throw InconsistentClientDataTypeException("ClientData.type must be 'get' on authentication, but it isn't.")
        }

        ChallengeVerifier.verify(collectedClientData, serverProperty)

        object : OriginVerifierImpl() {
            fun verifyIt(collectedClientData: CollectedClientData, serverProperty: ServerProperty) {
                verify(collectedClientData, serverProperty)
            }
        }.verifyIt(collectedClientData, serverProperty)

        CrossOriginFlagVerifier.verify(collectedClientData, conf.allowCrossOrigin)

        RpIdHashVerifier.verify(authenticatorData.rpIdHash, serverProperty)

        UPUVFlagsVerifier.verify(authenticatorData, conf.userPresence, conf.userVerification)

        BEBSFlagsVerifier.verify(authenticatorData)

        // BEFlagVerifier.verify(authenticator, authenticatorData)

        // AssertionSignatureVerifier().verify(authenticationData, authenticator.getAttestedCredentialData().getCOSEKey())

        /*
        val presentedSignCount: Long = authenticatorData.signCount
        val storedSignCount = authenticator.getCounter()
        if (presentedSignCount > 0 || storedSignCount > 0) {
            if (presentedSignCount > storedSignCount) {
                //no-op
            } else {
                maliciousCounterValueHandler.maliciousCounterValueDetected(authenticationObject)
            }
        }
         */
    }
}

abstract class WebAuthnOperation(val conf: WebAuthnConfig, opData: ExtOpData) : GTXOperation(opData) {
    fun verifyChallenge(clientData: CollectedClientData) {
        if (clientData.challenge.value.isEmpty()) {
            throw UserMistake("missing clientData.challenge")
        }
    }

    fun verifySignature(authenticatorDataBytes: ByteArray, clientDataJSON: String, alg: Long, publicKey: ByteArray, signature: ByteArray) {
        val signedData = authenticatorDataBytes + sha256Digest(clientDataJSON.toByteArray(Charsets.UTF_8))
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
