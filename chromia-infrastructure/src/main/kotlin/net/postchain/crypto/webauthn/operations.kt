package net.postchain.crypto.webauthn

import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.exception.VerificationException
import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.crypto.webauthn.webauthn4j.CustomCredentialRecord
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.ExtOpData

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

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) {
        webAuthnRegister(ctxt, data.args, true)
    }

    override fun checkCorrectness(ctxt: EContext) {
        webAuthnRegister(ctxt, data.args, false)
    }

    private fun webAuthnRegister(ctxt: EContext, args: Array<out Gtv>, isSyncing: Boolean) {
        if (args.size != 4) throw UserMistake("need 4 args, got ${args.size}")
        val id = args[0].asByteArray()
        val attestationObject = args[1].asByteArray()
        val clientDataJSON = args[2].asString()
        val transports = args[3].asArray().map { it.asString() }

        webAuthnRegister(ctxt, id, attestationObject, clientDataJSON, transports, isSyncing)
    }

    private fun webAuthnRegister(ctxt: EContext,
                                 id: ByteArray, attestationObjectBytes: ByteArray, clientDataJSON: String, transports: List<String>,
                                 isSyncing: Boolean) {
        verifyCredentialId(id)

        verifyAttestationObject(attestationObjectBytes)

        verifyClientData(clientDataJSON)

        verifyTransports(transports)

        val webAuthnManager = if (isSyncing) conf.nonStrictWebAuthnManager else conf.strictWebAuthnManager

        val registrationRequest = RegistrationRequest(
                attestationObjectBytes,
                clientDataJSON.toByteArray(Charsets.UTF_8),
                /*clientExtensionsJSON=*/null,
                transports.toSet(),
        )
        val registrationData = try {
            webAuthnManager.parse(registrationRequest)
        } catch (e: DataConversionException) {
            throw UserMistake(e.message ?: "verification failed")
        }
        val collectedClientData = registrationData.collectedClientData ?: throw UserMistake("invalid client data")

        verifyChallenge(ctxt, collectedClientData.challenge.value)

        val serverProperty = ServerProperty.builder()
                .origins(conf.allowedOrigins.toSet())
                .topOriginPredicate { conf.allowCrossOrigin }
                .rpId( conf.relyingPartyIdentifier)
                .challenge(collectedClientData.challenge)
                .build()

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
            webAuthnManager.verify(registrationData, registrationParameters)
        } catch (e: VerificationException) {
            throw UserMistake(e.message ?: "verification failed")
        }

        val attestationObject = verifiedRegistrationData.attestationObject
                ?: throw UserMistake("invalid attestationObject")
        val attestedCredentialData = attestationObject.authenticatorData.attestedCredentialData
                ?: throw UserMistake("invalid attestedCredentialData")
        val publicKey = conf.objectConverter.cborConverter.writeValueAsBytes(attestedCredentialData.coseKey)

        if (!attestedCredentialData.credentialId.contentEquals(id)) {
            throw UserMistake("credentialId mismatch")
        }

        if (conf.repository.fetchCredential(ctxt, id) != null) {
            throw UserMistake("credential with id ${id.toHex()} already registered")
        }

        credential = CredentialData(
                id = id.wrap(),
                aaguid = (attestedCredentialData.aaguid.bytes ?: AAGUID.ZERO.bytes!!).wrap(),
                publicKey = publicKey.wrap(),
                signCount = attestationObject.authenticatorData.signCount,
                transports = transports.joinToString(separator = ","),
                uvInitialized = attestationObject.authenticatorData.isFlagUV,
                backupEligible = attestationObject.authenticatorData.isFlagBE,
                backupState = attestationObject.authenticatorData.isFlagBS,
                suspiciousSignCountStored = null,
                suspiciousSignCountPresented = null,
        )
    }

    override fun apply(ctx: TxEContext): Boolean {
        persistChallenge(ctx)

        return credential?.let {
            conf.repository.persistCredential(ctx, data.opIndex, it)
            true
        } ?: false
    }
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

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) {
        webAuthnAuthenticate(ctxt, data.args, true)
    }

    override fun checkCorrectness(ctxt: EContext) {
        webAuthnAuthenticate(ctxt, data.args, false)
    }

    private fun webAuthnAuthenticate(ctxt: EContext, args: Array<out Gtv>, isSyncing: Boolean) {
        if (args.size != 4) throw UserMistake("need 4 args, got ${args.size}")
        val id = args[0].asByteArray()
        val authenticatorData = args[1].asByteArray()
        val clientDataJSON = args[2].asString()
        val signature = args[3].asByteArray()

        webAuthnAuthenticate(ctxt, id, authenticatorData, clientDataJSON, signature, isSyncing)
    }

    private fun webAuthnAuthenticate(ctxt: EContext,
                                     id: ByteArray, authenticatorDataBytes: ByteArray, clientDataJSON: String, signature: ByteArray,
                                     isSyncing: Boolean) {
        verifyCredentialId(id)

        verifyAuthenticatorData(authenticatorDataBytes)

        verifyClientData(clientDataJSON)

        val webAuthnManager = if (isSyncing) conf.nonStrictWebAuthnManager else conf.strictWebAuthnManager

        val authenticationRequest = AuthenticationRequest(
                /*credentialId=*/id,
                /*userHandle=*/null,
                authenticatorDataBytes,
                clientDataJSON.toByteArray(Charsets.UTF_8),
                /*clientExtensionsJSON=*/null,
                signature,
        )

        val authenticationData: AuthenticationData = try {
            webAuthnManager.parse(authenticationRequest)
        } catch (e: DataConversionException) {
            throw UserMistake(e.message ?: "verification failed")
        }
        val collectedClientData = authenticationData.collectedClientData ?: throw UserMistake("invalid client data")

        verifyChallenge(ctxt, collectedClientData.challenge.value)

        val credential = conf.repository.fetchCredential(ctxt, id)
                ?: throw UserMistake("credential with id ${id.toHex()} not registered")

        val coseKey = try {
            conf.objectConverter.cborConverter.readValue(credential.publicKey.data, COSEKey::class.java)
                    ?: throw UserMistake("invalid public key")
        } catch (e: DataConversionException) {
            throw UserMistake(e.message ?: "verification failed")
        }

        val attestedCredentialData = AttestedCredentialData(AAGUID(credential.aaguid.data), id, coseKey)

        val serverProperty = ServerProperty.builder()
                .origins(conf.allowedOrigins.toSet())
                .topOriginPredicate { conf.allowCrossOrigin }
                .rpId( conf.relyingPartyIdentifier)
                .challenge(collectedClientData.challenge)
                .build()

        val credentialRecord = CustomCredentialRecord(
                uvInitialized = credential.uvInitialized,
                backupEligible = credential.backupEligible,
                backupState = credential.backupState,
                credential.signCount,
                attestedCredentialData,
                credential.transports.split(",").map { AuthenticatorTransport.create(it) }.toSet(),
        )

        val authenticationParameters = AuthenticationParameters(
                serverProperty,
                credentialRecord,
                /*allowCredentials=*/null,
                conf.userVerification,
                conf.userPresence
        )

        try {
            webAuthnManager.verify(authenticationData, authenticationParameters)
        } catch (e: VerificationException) {
            throw UserMistake(e.message ?: "verification failed")
        }

        credentialRecord.suspiciousSignCount?.let { (presentedSignCount, storedSignCount) ->
            logger.info("Suspicious signCount value detected for credentialId=${id.toHex()}: authData.signCount=${presentedSignCount}, credentialRecord.signCount=${storedSignCount}")
        }

        this.credential = credential.copy(
                signCount = credentialRecord.counter,
                uvInitialized = credentialRecord.isUvInitialized!!,
                backupState = credentialRecord.isBackedUp!!,
                suspiciousSignCountPresented = credentialRecord.suspiciousSignCount?.first,
                suspiciousSignCountStored = credentialRecord.suspiciousSignCount?.second,
        )
    }

    override fun apply(ctx: TxEContext): Boolean {
        persistChallenge(ctx)

        return credential?.let {
            conf.repository.updateCredential(
                    ctx,
                    it.id.data,
                    it.signCount,
                    uvInitialized = it.uvInitialized,
                    backupState = it.backupState,
                    suspiciousSignCountPresented = it.suspiciousSignCountPresented,
                    suspiciousSignCountStored = it.suspiciousSignCountStored,
            )
            true
        } ?: false
    }
}

abstract class WebAuthnOperation(val conf: WebAuthnConfig, opData: ExtOpData) : GTXOperation(opData) {
    companion object {
        // https://w3c.github.io/webauthn/#credential-id
        const val MAX_CREDENTIAL_ID_SIZE = 1023

        // https://w3c.github.io/webauthn/#sctn-cryptographic-challenges
        const val CHALLENGE_MIN_SIZE = 16
        const val CHALLENGE_MAX_SIZE = 64

        const val MAX_ATTESTATION_OBJECT_SIZE = 4096
        const val MAX_AUTHENTICATOR_DATA_SIZE = 1024

        const val CLIENT_DATA_MAX_SIZE = 512

        const val MAX_TRANSPORTS = 6
        const val MAX_TRANSPORT_SIZE = 16
    }

    @Volatile
    protected var credential: CredentialData? = null

    @Volatile
    protected var challenge: ByteArray? = null

    fun verifyCredentialId(id: ByteArray) {
        if (id.isEmpty()) {
            throw UserMistake("empty id")
        }
        if (id.size > MAX_CREDENTIAL_ID_SIZE) {
            throw UserMistake("id too long, can be at most $MAX_CREDENTIAL_ID_SIZE bytes")
        }
    }

    fun verifyChallenge(ctxt: EContext, givenChallenge: ByteArray) {
        if (givenChallenge.size < CHALLENGE_MIN_SIZE) {
            throw UserMistake("challenge is too short, needs to be at least $CHALLENGE_MIN_SIZE bytes")
        }

        if (givenChallenge.size > CHALLENGE_MAX_SIZE) {
            throw UserMistake("challenge is too long")
        }

        if (conf.repository.challengeExists(ctxt, givenChallenge)) {
            throw UserMistake("challenge is not unique")
        }

        challenge = givenChallenge
    }

    fun verifyAttestationObject(attestationObjectBytes: ByteArray) {
        if (attestationObjectBytes.size > MAX_ATTESTATION_OBJECT_SIZE) {
            throw UserMistake("attestationObject is too large")
        }
    }

    fun verifyAuthenticatorData(authenticatorDataBytes: ByteArray) {
        if (authenticatorDataBytes.size > MAX_AUTHENTICATOR_DATA_SIZE) {
            throw UserMistake("authenticatorData is too large")
        }
    }

    fun verifyClientData(clientDataJSON: String) {
        if (clientDataJSON.length > CLIENT_DATA_MAX_SIZE) {
            throw UserMistake("clientData is too large")
        }
    }

    fun verifyTransports(transports: List<String>) {
        if (transports.size > MAX_TRANSPORTS) {
            throw UserMistake("too many transports, only $MAX_TRANSPORTS allowed")
        }

        for (transport in transports) {
            if (transport.length > MAX_TRANSPORT_SIZE) {
                throw UserMistake("too long transport, only $MAX_TRANSPORT_SIZE allowed")
            }
        }
    }

    fun persistChallenge(ctxt: BlockEContext) {
        challenge?.let { conf.repository.persistChallenge(ctxt, it) }
    }
}
