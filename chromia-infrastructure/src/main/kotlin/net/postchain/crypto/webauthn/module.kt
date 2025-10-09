package net.postchain.crypto.webauthn

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.client.Origin
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
)

data class WebAuthnConfig(
        val allowedOrigins: List<Origin>,

        val allowCrossOrigin: Boolean,

        val relyingPartyIdentifier: String,

        val userPresence: Boolean,

        val userVerification: Boolean,

        val objectConverter: ObjectConverter,

        val webAuthnManager: WebAuthnManager,

        val repository: WebAuthnRepository,
)

@Suppress("unused")
class WebAuthnGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): WebAuthnGTXModule {
        val configData = config.asDict()["webauthn"]!!.toObject<WebAuthnConfigData>()
        val objectConverter = ObjectConverter()
        val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
        return WebAuthnGTXModule(WebAuthnConfig(
                allowedOrigins = configData.allowedOrigins.map { Origin(it) },
                allowCrossOrigin = configData.allowCrossOrigin,
                relyingPartyIdentifier = configData.relyingPartyIdentifier,
                userPresence = configData.userPresence,
                userVerification = configData.userVerification,
                objectConverter = objectConverter,
                webAuthnManager = webAuthnManager,
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
