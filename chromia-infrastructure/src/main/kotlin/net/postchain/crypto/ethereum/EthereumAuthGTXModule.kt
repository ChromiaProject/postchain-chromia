package net.postchain.crypto.ethereum

import net.postchain.core.EContext
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.SimpleGTXModule

@Suppress("unused")
class EthereumAuthGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(
                CheckSigERC191Personal.OP_NAME to ::CheckSigERC191Personal,
        ),
        mapOf()
), MetadataProvider {
    override fun initializeDB(ctx: EContext) {}

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    CheckSigERC191Personal.OP_NAME to CheckSigERC191Personal.metadata,
            ),
            queries = mapOf())
}
