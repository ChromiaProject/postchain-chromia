package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.withWriteConnection
import net.postchain.common.reflection.newInstanceOf
import net.postchain.containers.ContainerRateLimit
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.crypto.KeyPair
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.PostchainContextAware
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import java.time.Instant

@Suppress("unused")
class HybridComputeSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) :
        SynchronizationInfrastructureExtension {
    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val configuration = process.blockchainEngine.getConfiguration()
        if (configuration is GTXModuleAware) {
            configuration.module.getSpecialTxExtensions().filterIsInstance<HybridComputeSpecialTransactionExtension>().firstOrNull()?.let { txExt ->
                val config = configuration.rawConfig.asDict()["hybridcompute"]?.toObject<HybridComputeConfig>()
                        ?: throw IllegalArgumentException("hybridcompute configuration not found")
                require(config.concurrency in 1..Int.MAX_VALUE) { "concurrency must be greater than 0" }
                require(config.blockBuildingIntervalMillis > 0) { "block_building_interval_millis must be greater than 0" }
                val engineNames = config.engines.ifEmpty {
                    if (config.engine.isNotEmpty()) listOf(config.engine) else listOf()
                }
                val fastEngineNames = config.fastEngines
                var container: String? = null
                var containerCreationTime: Instant? = null
                var containerRateLimits: Map<String, ContainerRateLimit> = mapOf()
                if (configuration is ManagedDataSourceAware) {
                    val dataSource = configuration.dataSource
                    if (dataSource is DirectoryDataSource) {
                        container = dataSource.getContainerForBlockchain(configuration.blockchainRid)
                        containerCreationTime = dataSource.getContainerCreationTime(container)
                        containerRateLimits = dataSource.getContainerRateLimits(container)
                        logger.info("Running in container $container which was created at $containerCreationTime")
                    }
                }
                val engines = engineNames.map { newInstanceOf<HybridComputeEngine>(it) }
                val fastEngines = fastEngineNames.map { newInstanceOf<HybridComputeEngine>(it) }
                val allEngines = engines + fastEngines
                require(allEngines.isNotEmpty()) { "there must be at least one engine" }
                require(allEngines.size == allEngines.map { it.name }.toSet().size) { "all engines must have unique names" }
                allEngines.filterIsInstance<PostchainContextAware>().forEach {
                    withWriteConnection(postchainContext.blockBuilderStorage, configuration.chainID) { ctx ->
                        it.initializeContext(configuration, postchainContext, ctx)
                        true
                    }
                }
                txExt.load(
                        configuration.module,
                        configuration.chainID,
                        configuration.effectiveBlockchainRID,
                        postchainContext.cryptoSystem,
                        KeyPair(postchainContext.appConfig.pubKeyByteArray, postchainContext.appConfig.privKeyByteArray),
                        container,
                        containerCreationTime,
                        containerRateLimits,
                        concurrency = config.concurrency.toInt(),
                        loadTimeoutSeconds = config.loadTimeoutSeconds,
                        computeTimeoutSeconds = config.computeTimeoutSeconds,
                        computeClusterTimeoutSeconds = config.computeClusterTimeoutSeconds,
                        blockBuildingIntervalMillis = config.blockBuildingIntervalMillis,
                        sharedStorage = postchainContext.sharedStorage,
                        engineList = engines,
                        fastEngineList = fastEngines,
                )
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
