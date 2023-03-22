package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.client.config.FailOverConfig
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.ClusterManagementFactory
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import java.time.Duration

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) :
        SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, MutableList<Shutdownable>>()
    private val dbOperations = IcmfDatabaseOperationsImpl()
    private val cryptoSystem = postchainContext.cryptoSystem

    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is GTXModuleAware) {
            getIcmfReceiverSpecialTxExtension(configuration.module)?.let { txExt ->
                val clusterManagement = createClusterManagement(configuration)
                val clientProvider = createClientProvider(clusterManagement)
                txExt.clusterManagement = clusterManagement

                val blockStrategyConfig = configuration.rawConfig[KEY_BLOCKSTRATEGY] ?: gtv(mapOf())
                txExt.maxBlockSize = blockStrategyConfig.toObject<BaseBlockBuildingStrategyConfigurationData>().maxBlockSize
                if (txExt.maxBlockSize < MAX_MESSAGE_SIZE + BLOCK_SIZE_MARGIN) {
                    logger.warn("Configured max block size ${txExt.maxBlockSize} for blockchain is lower than recommended minimum size for ICMF message reception ${MAX_MESSAGE_SIZE + BLOCK_SIZE_MARGIN}")
                }

                val queryProvider = createQueryProvider(configuration, clusterManagement)

                val rawIcmfReceiverConfig = configuration.rawConfig["icmf"]?.get("receiver")
                        ?: throw UserMistake("Missing configuration key icmf/receiver")
                val config = IcmfReceiverBlockchainConfigData.fromGtv(rawIcmfReceiverConfig)
                txExt.icmfReceiverBlockchainConfigData = config

                if (config.global != null) {
                    if (!config.global.topics.isNullOrEmpty()) {
                        val globalTopicIcmfReceiver = GlobalTopicIcmfReceiver(
                                config.global.topics.distinct().associateWith { listOf() },
                                cryptoSystem,
                                engine.storage,
                                queryProvider,
                                configuration.chainID,
                                configuration.blockchainRid,
                                clusterManagement,
                                clientProvider,
                                dbOperations
                        )
                        receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(globalTopicIcmfReceiver)
                        txExt.globalTopicReceivers.add(globalTopicIcmfReceiver)
                    }

                    if (!config.global.blockchains.isNullOrEmpty()) {
                        val specificChainReceiver = GlobalTopicIcmfReceiver(
                                config.global.blockchains.groupBy { it.topic }
                                        .mapValues { it.value.map { x -> BlockchainRid(x.blockchainRid) }.distinct() },
                                cryptoSystem,
                                engine.storage,
                                queryProvider,
                                configuration.chainID,
                                configuration.blockchainRid,
                                clusterManagement,
                                clientProvider,
                                dbOperations
                        )
                        receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(specificChainReceiver)
                        txExt.globalTopicReceivers.add(specificChainReceiver)
                    }
                }

                if (config.local != null) {
                    val origins = config.local.map { it.topic to BlockchainRid(it.blockchainRid) }
                    val intraClusterTopicIcmfReceiver = IntraClusterTopicIcmfReceiver(
                            origins,
                            queryProvider
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(intraClusterTopicIcmfReceiver)
                    txExt.intraClusterReceivers.add(intraClusterTopicIcmfReceiver)
                }

                if (config.anchoring != null) {
                    val anchoringReceiver = AnchoringIcmfReceiver(
                            config.anchoring.topics,
                            clusterManagement,
                            queryProvider
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(anchoringReceiver)
                    txExt.anchoringReceivers.add(anchoringReceiver)
                }
            }
        }
    }

    open fun createClusterManagement(configuration: BlockchainConfiguration): ClusterManagement =
            ClusterManagementFactory.create(configuration, postchainContext.connectionManager)

    open fun createClientProvider(clusterManagement: ClusterManagement): ChromiaClientProvider = ChromiaClientProvider(
            failOverConfig = FailOverConfig(
                    attemptsPerEndpoint = 1,
                    attemptInterval = Duration.ZERO
            ), clusterManagement
    )

    open fun createQueryProvider(
            configuration: BlockchainConfiguration,
            clusterManagement: ClusterManagement
    ): ChromiaQueryProvider =
            ChromiaQueryProviderFactory.create(configuration, postchainContext.blockQueriesProvider, postchainContext.connectionManager, clusterManagement)

    override fun disconnectProcess(process: BlockchainProcess) {
        receivers.remove(process.blockchainEngine.getConfiguration().chainID)?.forEach { it.shutdown() }
    }

    override fun shutdown() {
        receivers.values.forEach { chain -> chain.forEach { it.shutdown() } }
    }

    private fun getIcmfReceiverSpecialTxExtension(module: GTXModule): IcmfReceiverSpecialTxExtension? {
        return module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is IcmfReceiverSpecialTxExtension)
        } as IcmfReceiverSpecialTxExtension?
    }
}
