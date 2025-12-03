package net.postchain.crypto.webauthn

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.client.Origin
import com.webauthn4j.metadata.CertPathCheckContext
import com.webauthn4j.metadata.DefaultCertPathChecker
import com.webauthn4j.metadata.anchor.MetadataBLOBBasedTrustAnchorRepository
import com.webauthn4j.metadata.data.MetadataBLOB
import com.webauthn4j.metadata.data.MetadataBLOBFactory
import com.webauthn4j.metadata.exception.MDSException
import com.webauthn4j.verifier.CoreAuthenticationObject
import com.webauthn4j.verifier.attestation.statement.androidkey.AndroidKeyAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.androidsafetynet.AndroidSafetyNetAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.apple.AppleAnonymousAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.packed.PackedAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.tpm.TPMAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.u2f.FIDOU2FAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier
import com.webauthn4j.verifier.attestation.trustworthiness.self.DefaultSelfAttestationTrustworthinessVerifier
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.crypto.webauthn.webauthn4j.CustomCredentialRecord
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.QueryMetadata
import net.postchain.gtx.ReturnMetadata
import net.postchain.gtx.SimpleGTXModule
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.LocalDate

const val QUERY_WEBAUTHN_GET_CREDENTIAL = "gtxc.webauthn_get_credential"

data class WebAuthnConfigData(
        @param:Name("allowed-origins")
        val allowedOrigins: List<String>, // https://w3c.github.io/webauthn/#dom-collectedclientdata-origin

        @param:Name("allow-cross-origin")
        val allowCrossOrigin: Boolean, // https://w3c.github.io/webauthn/#dom-collectedclientdata-crossorigin

        @param:Name("relying-party-identifier")
        val relyingPartyIdentifier: String, // https://w3c.github.io/webauthn/#rp-id

        @param:Name("user-presence")
        @param:DefaultValue(defaultBoolean = true)
        val userPresence: Boolean, // https://w3c.github.io/webauthn/#concept-user-present

        @param:Name("user-verification")
        @param:DefaultValue(defaultBoolean = false)
        val userVerification: Boolean, // https://w3c.github.io/webauthn/#user-verification

        @param:Name("verify-attestation")
        @param:DefaultValue(defaultBoolean = false)
        val verifyAttestation: Boolean, // https://w3c.github.io/webauthn/#reg-ceremony-verify-attestation
)

data class WebAuthnConfig(
        val allowedOrigins: List<Origin>,
        val allowCrossOrigin: Boolean,
        val relyingPartyIdentifier: String,
        val userPresence: Boolean,
        val userVerification: Boolean,
        val objectConverter: ObjectConverter,
        val strictWebAuthnManager: WebAuthnManager,
        val nonStrictWebAuthnManager: WebAuthnManager,
        val repository: WebAuthnRepository,
)

@Suppress("unused")
class WebAuthnGTXModuleFactory : GTXModuleFactory {
    companion object : KLogging() {
        // refresh this root certificate before it expires at 2029-03-18: https://valid.r3.roots.globalsign.com/
        val fidoMDSTrustAnchor = loadTrustAnchor("/net/postchain/crypto/webauthn/root-r3.crt")

        internal fun loadTrustAnchor(resourcePath: String): TrustAnchor {
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = WebAuthnGTXModule::class.java.getResourceAsStream(resourcePath).use {
                if (it == null) throw MDSException("Classpath resource not found: $resourcePath")
                certFactory.generateCertificate(it) as X509Certificate
            }
            try {
                cert.checkValidity()
            } catch (e: CertificateException) {
                logger.error("FIDO MDS root certificate is not valid: ${e.message}, get a new one at https://valid.r3.roots.globalsign.com/")
            }
            return TrustAnchor(cert, null)
        }

        // TODO WebAuthn: refresh this FIDO MDS3 blob monthly: https://fidoalliance.org/metadata/
        //                next update 2026-01-01
        val fidoMDSMetadataBLOB = loadMetadataBLOB(ObjectConverter(), "/net/postchain/crypto/webauthn/fido-mds3.blob", setOf(fidoMDSTrustAnchor))

        internal fun loadMetadataBLOB(objectConverter: ObjectConverter, resourcePath: String, trustAnchors: Set<TrustAnchor>): MetadataBLOB {
            val metadataBLOBFactory = MetadataBLOBFactory(objectConverter)
            val data = WebAuthnGTXModule::class.java.getResourceAsStream(resourcePath)?.use {
                it.readAllBytes().toString(Charsets.UTF_8)
            } ?: throw MDSException("Classpath resource not found: $resourcePath")
            val metadataBLOB = metadataBLOBFactory.parse(data)
            if (!metadataBLOB.isValidSignature) {
                throw MDSException("MetadataBLOB signature is invalid")
            }
            validateCertPath(metadataBLOB, trustAnchors)
            val nextUpdate = metadataBLOB.payload.nextUpdate
            if (LocalDate.now().isAfter(nextUpdate)) {
                logger.warn("FIDO MDS3 blob is expired (nextUpdate: $nextUpdate), get a new one at https://fidoalliance.org/metadata/")
            }
            return metadataBLOB
        }

        private fun validateCertPath(metadataBLOB: MetadataBLOB, trustAnchors: Set<TrustAnchor>) {
            val certPath = metadataBLOB.header.x5c ?: throw MDSException("MetadataBLOB certificate chain missing")
            DefaultCertPathChecker().check(CertPathCheckContext(certPath, trustAnchors, true))
        }

        internal fun createWebAuthnManager(objectConverter: ObjectConverter, verifyAttestation: Boolean): Pair<WebAuthnManager, WebAuthnManager> {
            val (strictWebAuthnManager, nonStrictWebAuthnManager) = if (verifyAttestation) {
                val certPathTrustworthinessVerifier = DefaultCertPathTrustworthinessVerifier(
                        MetadataBLOBBasedTrustAnchorRepository({ fidoMDSMetadataBLOB }))
                val selfAttestationTrustworthinessVerifier = DefaultSelfAttestationTrustworthinessVerifier(false)
                WebAuthnManager(
                        listOf(
                                FIDOU2FAttestationStatementVerifier(),
                                PackedAttestationStatementVerifier(),
                                TPMAttestationStatementVerifier(),
                                AndroidKeyAttestationStatementVerifier(),
                                AndroidSafetyNetAttestationStatementVerifier(),
                                AppleAnonymousAttestationStatementVerifier(),
                        ),
                        certPathTrustworthinessVerifier,
                        selfAttestationTrustworthinessVerifier,
                        listOf(),
                        listOf(),
                        objectConverter,
                ) to WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
            } else {
                val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
                webAuthnManager to webAuthnManager
            }

            val maliciousCounterValueHandler = { authenticationObject: CoreAuthenticationObject ->
                val presentedSignCount = authenticationObject.authenticatorData.signCount
                val storedSignCount = authenticationObject.authenticator.counter
                (authenticationObject.authenticator as? CustomCredentialRecord)?.suspiciousSignCount = presentedSignCount to storedSignCount
            }
            strictWebAuthnManager.authenticationDataVerifier.setMaliciousCounterValueHandler(maliciousCounterValueHandler)
            nonStrictWebAuthnManager.authenticationDataVerifier.setMaliciousCounterValueHandler(maliciousCounterValueHandler)

            return strictWebAuthnManager to nonStrictWebAuthnManager
        }
    }

    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): WebAuthnGTXModule {
        val configData = config.asDict()["webauthn"]?.toObject<WebAuthnConfigData>()
                ?: throw UserMistake("No 'webauthn' in blockchain config")
        if (configData.allowedOrigins.isEmpty()) {
            throw UserMistake("'webauthn.allowed-origins' must not be empty")
        }
        val allowedOrigins = try {
            configData.allowedOrigins.map { Origin(it) }
        } catch (e: Exception) {
            throw UserMistake("Invalid origin format in 'webauthn.allowed-origins': ${e.message}")
        }
        if (configData.relyingPartyIdentifier.isBlank()) {
            throw UserMistake("'relying-party-identifier' must not be empty")
        }
        val objectConverter = ObjectConverter()
        val (strictWebAuthnManager, nonStrictWebAuthnManager) = createWebAuthnManager(objectConverter, configData.verifyAttestation)
        return WebAuthnGTXModule(WebAuthnConfig(
                allowedOrigins = allowedOrigins,
                allowCrossOrigin = configData.allowCrossOrigin,
                relyingPartyIdentifier = configData.relyingPartyIdentifier,
                userPresence = configData.userPresence,
                userVerification = configData.userVerification,
                objectConverter = objectConverter,
                strictWebAuthnManager = strictWebAuthnManager,
                nonStrictWebAuthnManager = nonStrictWebAuthnManager,
                repository = WebAuthnRepositoryImpl(),
        ))
    }
}

class WebAuthnGTXModule(conf: WebAuthnConfig) : SimpleGTXModule<WebAuthnConfig>(
        conf,
        mapOf(
                WebAuthnRegister.OP_NAME to ::WebAuthnRegister,
                WebAuthnAuthenticate.OP_NAME to ::WebAuthnAuthenticate,
        ),
        mapOf(
                QUERY_WEBAUTHN_GET_CREDENTIAL to { conf, ctxt, args ->
                    val dict = args as GtvDictionary
                    val id = dict["id"]?.asByteArray() ?: throw UserMistake("No id argument supplied")
                    val credential = conf.repository.fetchCredential(ctxt, id)
                    if (credential?.deleted ?: true) GtvNull else credential.toGtv()
                },
        )
), MetadataProvider /* TODO enable snapshots , SnapshotAware by conf.repository */ {
    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    WebAuthnRegister.OP_NAME to WebAuthnRegister.metadata,
                    WebAuthnAuthenticate.OP_NAME to WebAuthnAuthenticate.metadata,
            ),
            queries = mapOf(
                    QUERY_WEBAUTHN_GET_CREDENTIAL to QueryMetadata(args = listOf(
                            ArgumentMetadata(name = "id", gtvTypes = setOf(GtvType.BYTEARRAY)),
                    ), returnType = ReturnMetadata(gtvTypes = setOf(GtvType.DICT, GtvType.NULL))),
            ))

    override fun initializeDB(ctx: EContext) {
        conf.repository.initializeDB(ctx)
    }
}
