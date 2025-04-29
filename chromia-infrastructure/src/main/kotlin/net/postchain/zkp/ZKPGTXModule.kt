package net.postchain.zkp

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.zkp.plonk.PlonkVerificationKey
import net.postchain.zkp.plonk.PlonkVerifyGTXOperation
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule

class ZKPGTXModule : SimpleGTXModule<ZKPGTXModuleContext>(
        ZKPGTXModuleContext(),
        mapOf(PlonkVerifyGTXOperation.OP_NAME to ::PlonkVerifyGTXOperation),
        mapOf()
), PostchainContextAware {
    override fun initializeDB(ctx: EContext) {}

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        conf.plonk = ZKPGTXPlonkModuleContext(
                configuration.rawConfig["zkp"]?.get("plonk")?.get("verification_keys")?.asDict()?.map {
                    it.key to it.value.toObject<PlonkVerificationKey>()
                }?.toMap() ?: mapOf()
        )
    }
}
