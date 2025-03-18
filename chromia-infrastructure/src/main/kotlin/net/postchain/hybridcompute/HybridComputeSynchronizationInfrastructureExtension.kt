package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleAware

@Suppress("unused")
class HybridComputeSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        SynchronizationInfrastructureExtension {
    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        if (configuration is GTXModuleAware) {
            configuration.module.getSpecialTxExtensions().filterIsInstance<HybridComputeSpecialTransactionExtension>().firstOrNull()?.let { txExt ->
                val config = configuration.rawConfig.asDict()["hybridcompute"]?.toObject<HybridComputeConfig>()
                        ?: throw UserMistake("hybridcompute configuration not found")
                require(config.computeTimeoutSeconds > 0) { "compute_timeout_seconds must be greater than 0" }
                require(config.concurrency > 0) { "concurrency must be greater than 0" }
                val engine = newInstanceOf<HybridComputeEngine>(config.engine)
                engine.init(configuration.rawConfig, configuration.blockchainRid)
                txExt.config = config
                txExt.engine = engine
                txExt.load()
            }
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        if (configuration is GTXModuleAware) {
            configuration.module.getSpecialTxExtensions().filterIsInstance<HybridComputeSpecialTransactionExtension>().firstOrNull()
                    ?.shutdown()
        }
    }

    override fun shutdown() {}
}
