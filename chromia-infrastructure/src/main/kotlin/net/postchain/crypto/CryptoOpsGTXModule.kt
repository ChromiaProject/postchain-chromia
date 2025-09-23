package net.postchain.crypto

import net.postchain.core.EContext
import net.postchain.crypto.webauthn.CheckSigWebAuthnAuthenticate
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.SimpleGTXModule

class CryptoOpsGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(
                CheckSigERC191Personal.OP_NAME to ::CheckSigERC191Personal,
                CheckSigWebAuthnAuthenticate.OP_NAME to ::CheckSigWebAuthnAuthenticate,
        ),
        mapOf()
), MetadataProvider {
    override fun initializeDB(ctx: EContext) {}

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    CheckSigERC191Personal.OP_NAME to CheckSigERC191Personal.metadata,
                    CheckSigWebAuthnAuthenticate.OP_NAME to CheckSigWebAuthnAuthenticate.metadata,
            ),
            queries = mapOf())
}
