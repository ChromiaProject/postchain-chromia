package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.base.configuration.KEY_GTX
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.TryNextOnErrorRequestStrategyFactory
import net.postchain.client.request.EndpointPool
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.PostchainQueryFactory
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.config.ManagedBlockchainConfigProvider
import net.postchain.d1.nm_api.NodeManagementImpl
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GtxConfigurationData
import net.postchain.managed.config.ManagedDataSourceAware
import java.time.Duration
import kotlin.math.min

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) :
        SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, MutableList<Shutdownable>>()
    private val dbOperations = IcmfDatabaseOperationsImpl()
    private val cryptoSystem = postchainContext.cryptoSystem

    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is GTXModuleAware && configuration is ManagedDataSourceAware) {
            getIcmfReceiverSpecialTxExtension(configuration.module)?.let { txExt ->
                val clusterManagement = createClusterManagement(configuration)
                val clientProvider = createClientProvider(clusterManagement)
                val blockchainConfigProvider = createBlockchainConfigProvider(configuration, clusterManagement)
                txExt.blockchainConfigProvider = blockchainConfigProvider
                txExt.clusterManagement = clusterManagement
                txExt.isSigner = process::isSigner

                val blockStrategyConfig = configuration.rawConfig[KEY_BLOCKSTRATEGY] ?: gtv(mapOf())
                val maxBlockSize = blockStrategyConfig.toObject<BaseBlockBuildingStrategyConfigurationData>().maxBlockSize
                val gtxConfig = configuration.rawConfig[KEY_GTX] ?: gtv(mapOf())
                val maxTxSize = gtxConfig.toObject<GtxConfigurationData>().maxTxSize
                txExt.maxTxSize = min(maxBlockSize, maxTxSize)

                val queryProvider = createQueryProvider(configuration, clusterManagement)

                val rawIcmfReceiverConfig = configuration.rawConfig["icmf"]?.get("receiver")
                        ?: throw UserMistake("Missing configuration key icmf/receiver")
                val config = IcmfReceiverBlockchainConfigData.fromGtv(rawIcmfReceiverConfig)
                txExt.icmfReceiverBlockchainConfigData = config
                txExt.specialTxSizeMargin = config.specialTxMarginBytes

                if (maxBlockSize < MAX_MESSAGE_SIZE + config.specialTxMarginBytes) {
                    logger.warn("Configured max block size $maxBlockSize for blockchain is lower than recommended minimum size for ICMF message reception ${MAX_MESSAGE_SIZE + config.specialTxMarginBytes}")
                }
                if (maxTxSize < MAX_MESSAGE_SIZE + config.specialTxMarginBytes) {
                    logger.warn("Configured max tx size $maxTxSize for blockchain is lower than recommended minimum size for ICMF message reception ${MAX_MESSAGE_SIZE + config.specialTxMarginBytes}")
                }

                if (config.global != null) {
                    if (!config.global.topics.isNullOrEmpty()) {
                        val globalTopicIcmfReceiver = GlobalTopicIcmfReceiver(
                                config.global.topics.distinct().associateWith { listOf() },
                                cryptoSystem,
                                engine.blockBuilderStorage,
                                queryProvider,
                                configuration.chainID,
                                configuration.blockchainRid,
                                clusterManagement,
                                blockchainConfigProvider,
                                clientProvider,
                                dbOperations
                        )
                        receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(globalTopicIcmfReceiver)
                        txExt.anchoredReceivers.add(globalTopicIcmfReceiver)
                    }

                    if (!config.global.blockchains.isNullOrEmpty()) {
                        val specificChainReceiver = GlobalTopicIcmfReceiver(
                                config.global.blockchains.groupBy { it.topic }
                                        .mapValues { it.value.map { x -> BlockchainRid(x.blockchainRid) }.distinct() },
                                cryptoSystem,
                                engine.blockBuilderStorage,
                                queryProvider,
                                configuration.chainID,
                                configuration.blockchainRid,
                                clusterManagement,
                                blockchainConfigProvider,
                                clientProvider,
                                dbOperations
                        )
                        receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(specificChainReceiver)
                        txExt.anchoredReceivers.add(specificChainReceiver)
                    }
                }

                if (config.local != null || config.directoryChain != null) {
                    val directoryChainOrigins = config.directoryChain?.let { directoryChainConfig ->
                        val directoryChainBrid = withReadConnection(engine.blockBuilderStorage, 0L) { ctx ->
                            DatabaseAccess.of(ctx).getBlockchainRid(ctx)
                        } ?: throw ProgrammerMistake("Unable to resolve directory chain blockchain RID")
                        txExt.directoryChainBrid = directoryChainBrid
                        directoryChainConfig.topics.map { LocalIcmfOrigin(it, directoryChainBrid) }
                    } ?: listOf()
                    val localOrigins = config.local?.map {
                        LocalIcmfOrigin(it.topic, BlockchainRid(it.blockchainRid), it.skipToHeight)
                    } ?: listOf()
                    val localTopicIcmfReceiver = LocalTopicIcmfReceiver(
                            directoryChainOrigins + localOrigins,
                            queryProvider,
                            clusterManagement,
                            clientProvider,
                            configuration.chainID,
                            configuration.blockchainRid,
                            engine.blockBuilderStorage,
                            dbOperations
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(localTopicIcmfReceiver)
                    txExt.nonAnchoredReceivers.add(localTopicIcmfReceiver)
                }

                if (config.anchoring != null) {
                    val anchoringReceiver = AnchoringIcmfReceiver(
                            config.anchoring.topics,
                            clusterManagement,
                            queryProvider
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(anchoringReceiver)
                    txExt.nonAnchoredReceivers.add(anchoringReceiver)
                }
            }
        }
    }

    open fun createClusterManagement(configuration: BlockchainConfiguration): ClusterManagement =
            ClusterManagementImpl(PostchainQueryFactory.create(configuration, postchainContext.connectionManager))

    open fun createBlockchainConfigProvider(configuration: ManagedDataSourceAware, clusterManagement: ClusterManagement): BlockchainConfigProvider =
            ManagedBlockchainConfigProvider(
                    NodeManagementImpl { name, gtv -> configuration.dataSource.query(name, gtv) },
                    clusterManagement
            )

    open fun createClientProvider(clusterManagement: ClusterManagement): ChromiaClientProvider = ChromiaClientProvider(
            clusterManagement, PostchainClientConfig(BlockchainRid.ZERO_RID, EndpointPool.singleUrl(""),
            failOverConfig = FailOverConfig(
                    attemptsPerEndpoint = 1,
                    attemptInterval = Duration.ZERO
            ),
            requestStrategy = TryNextOnErrorRequestStrategyFactory()
    ))

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
