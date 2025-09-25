package net.postchain.crypto.webauthn

import com.webauthn4j.data.client.Origin
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.SimpleGTXModule

data class WebAuthnConfigData(
        @param:Name("allowed-origins")
        val allowedOrigins: List<String>, // https://w3c.github.io/webauthn/#dom-collectedclientdata-origin

        @param:Name("allow-cross-origin")
        val allowCrossOrigin: Boolean, // https://w3c.github.io/webauthn/#dom-collectedclientdata-crossorigin

        @param:Name("allowed-relying-party-identifiers")
        val allowedRelyingPartyIdentifiers: List<String>, // https://w3c.github.io/webauthn/#rp-id
)

data class WebAuthnConfig(
        val allowedOrigins: List<Origin>,

        val allowCrossOrigin: Boolean,

        val allowedRelyingPartyIdentifiers: List<String>,
)

@Suppress("unused")
class WebAuthnGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): WebAuthnGTXModule {
        val configData = config.asDict()["webauthn"]!!.toObject<WebAuthnConfigData>()
        return WebAuthnGTXModule(WebAuthnConfig(
                allowedOrigins = configData.allowedOrigins.map { Origin(it) },
                allowCrossOrigin = configData.allowCrossOrigin,
                allowedRelyingPartyIdentifiers = configData.allowedRelyingPartyIdentifiers,
        ))
    }
}

class WebAuthnGTXModule(conf: WebAuthnConfig) : SimpleGTXModule<WebAuthnConfig>(
        conf,
        mapOf(
                CheckSigWebAuthnAuthenticate.OP_NAME to ::CheckSigWebAuthnAuthenticate,
        ),
        mapOf()
), MetadataProvider {
    override fun initializeDB(ctx: EContext) {}

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    CheckSigWebAuthnAuthenticate.OP_NAME to CheckSigWebAuthnAuthenticate.metadata,
            ),
            queries = mapOf())
}
