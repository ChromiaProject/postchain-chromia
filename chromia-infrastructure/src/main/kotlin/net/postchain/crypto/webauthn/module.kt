package net.postchain.crypto.webauthn

import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.SimpleGTXModule

data class WebAuthnConfig(
        @param:Name("allowed-relying-party-identifiers")
        val allowedRelyingPartyIdentifiers: List<String>, // https://w3c.github.io/webauthn/#rp-id
)

@Suppress("unused")
class WebAuthnGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): WebAuthnGTXModule {
        val moduleConfig = config.asDict()["webauthn"]!!.toObject<WebAuthnConfig>()
        return WebAuthnGTXModule(moduleConfig)
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
