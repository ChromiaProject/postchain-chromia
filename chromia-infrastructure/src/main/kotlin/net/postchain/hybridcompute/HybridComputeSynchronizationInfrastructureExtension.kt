package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleAware
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUESTS
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAware

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
                require(config.loadTimeoutSeconds > 0) { "load_timeout_seconds must be greater than 0" }
                require(config.computeTimeoutSeconds > 0) { "compute_timeout_seconds must be greater than 0" }
                require(config.concurrency > 0) { "concurrency must be greater than 0" }
                if (configuration is ManagedDataSourceAware) {
                    val dataSource = configuration.dataSource
                    if (dataSource is DirectoryDataSource) {
                        val container = dataSource.getContainerForBlockchain(configuration.blockchainRid)
                        val containerCreationTime = dataSource.getContainerCreationTime(container)
                        val containerRateLimits = dataSource.getContainerRateLimits(container)
                        logger.info("Running in container $container which was created at $containerCreationTime")
                        txExt.container = container
                        txExt.containerCreationTime = containerCreationTime
                        txExt.containerRateLimits = containerRateLimits
                    }
                }
                val engine = newInstanceOf<HybridComputeEngine>(config.engine)
                engine.init(configuration.rawConfig, configuration.blockchainRid)
                txExt.config = config
                txExt.engine = engine
                txExt.hasDistributedTimeout = GET_TAKEN_REQUESTS in configuration.module.getQueries() && config.computeClusterTimeoutSeconds > 0
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
