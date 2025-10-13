package net.postchain.crypto.webauthn

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.client.Origin
import com.webauthn4j.metadata.CertPathCheckContext
import com.webauthn4j.metadata.DefaultCertPathChecker
import com.webauthn4j.metadata.anchor.MetadataBLOBBasedTrustAnchorRepository
import com.webauthn4j.metadata.data.MetadataBLOB
import com.webauthn4j.metadata.data.MetadataBLOBFactory
import com.webauthn4j.metadata.exception.CertPathCheckException
import com.webauthn4j.metadata.exception.MDSException
import com.webauthn4j.verifier.attestation.statement.androidkey.AndroidKeyAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.androidsafetynet.AndroidSafetyNetAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.apple.AppleAnonymousAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.packed.PackedAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.tpm.TPMAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.statement.u2f.FIDOU2FAttestationStatementVerifier
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier
import com.webauthn4j.verifier.attestation.trustworthiness.self.DefaultSelfAttestationTrustworthinessVerifier
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.SnapshotAware
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

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
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): WebAuthnGTXModule {
        val configData = config.asDict()["webauthn"]!!.toObject<WebAuthnConfigData>()
        val objectConverter = ObjectConverter()
        val (strictWebAuthnManager, nonStrictWebAuthnManager) = createWebAuthnManager(objectConverter, configData.verifyAttestation)
        return WebAuthnGTXModule(WebAuthnConfig(
                allowedOrigins = configData.allowedOrigins.map { Origin(it) },
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
        mapOf()
), MetadataProvider, SnapshotAware by conf.repository {
    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    WebAuthnRegister.OP_NAME to WebAuthnRegister.metadata,
                    WebAuthnAuthenticate.OP_NAME to WebAuthnAuthenticate.metadata,
            ),
            queries = mapOf())

    override fun initializeDB(ctx: EContext) {
        conf.repository.initializeDB(ctx)
    }
}

internal fun createWebAuthnManager(objectConverter: ObjectConverter, verifyAttestation: Boolean): Pair<WebAuthnManager, WebAuthnManager> = if (verifyAttestation) {
    // TODO WebAuthn: refresh this root certificate before it expires at 2029-03-18: https://valid.r3.roots.globalsign.com/
    val fidoMDSTrustAnchor = loadTrustAnchor("/net/postchain/crypto/webauthn/root-r3.crt")

    // TODO WebAuthn: refresh this FIDO MDS3 blob monthly: https://fidoalliance.org/metadata/
    //                next update 2025-11-01
    val fidoMDSMetadataBLOB = loadMetadataBLOB(objectConverter, "/net/postchain/crypto/webauthn/fido-mds3.blob", setOf(fidoMDSTrustAnchor))

    val certPathTrustworthinessVerifier = DefaultCertPathTrustworthinessVerifier(
            MetadataBLOBBasedTrustAnchorRepository({ fidoMDSMetadataBLOB }))
    val selfAttestationTrustworthinessVerifier = DefaultSelfAttestationTrustworthinessVerifier()
    selfAttestationTrustworthinessVerifier.isSelfAttestationAllowed = false
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

internal fun loadTrustAnchor(resourcePath: String): TrustAnchor {
    val certFactory = CertificateFactory.getInstance("X.509")
    val cert = WebAuthnGTXModule::class.java.getResourceAsStream(resourcePath).use {
        if (it == null) throw MDSException("Classpath resource not found: $resourcePath")
        certFactory.generateCertificate(it) as X509Certificate
    }
    return TrustAnchor(cert, null)
}

internal fun loadMetadataBLOB(objectConverter: ObjectConverter, resourcePath: String, trustAnchors: Set<TrustAnchor>): MetadataBLOB {
    val metadataBLOBFactory = MetadataBLOBFactory(objectConverter)
    val data = WebAuthnGTXModule::class.java.getResourceAsStream(resourcePath).use {
        if (it == null) throw MDSException("Classpath resource not found: $resourcePath")
        String(it.readAllBytes(), Charsets.UTF_8)
    }
    val metadataBLOB = metadataBLOBFactory.parse(data)
    if (!metadataBLOB.isValidSignature) {
        throw MDSException("MetadataBLOB signature is invalid")
    }
    validateCertPath(metadataBLOB, trustAnchors)
    return metadataBLOB
}

private fun validateCertPath(metadataBLOB: MetadataBLOB, trustAnchors: Set<TrustAnchor>) {
    val certPath = metadataBLOB.header.x5c
    try {
        DefaultCertPathChecker().check(CertPathCheckContext(certPath, trustAnchors, true))
    } catch (e: CertPathCheckException) {
        throw MDSException("MetadataBLOB certificate chain validation failed", e)
    }
}
