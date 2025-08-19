package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.base.configuration.KEY_GTX
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerBlockchainProcessManagerExtension
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.RemoteBlockchainProcess
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.d1.anchoring.check.AnchoringCheck
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.config.ManagedBlockchainConfigProvider
import net.postchain.d1.nm_api.NodeManagementImpl
import net.postchain.d1.query.BlockQueriesAdapter
import net.postchain.d1.query.MasterClient
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GtxConfigurationData
import net.postchain.managed.config.ManagedDataSourceAware
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

open class AnchoringProcessManagerExtension(
        private val postchainContext: PostchainContext,
        private val blockchainInfrastructure: BlockchainInfrastructure
) : ContainerBlockchainProcessManagerExtension, RemoteBlockchainProcessConnectable {

    companion object : KLogging()

    private val anchoringPipeManagers = mutableMapOf<Long, AnchoringPipeManager>()
    private val localProcessChainIds = ConcurrentHashMap<BlockchainRid, Long>()
    private val remoteProcessChainIds = ConcurrentHashMap<BlockchainRid, Long>()
    private val anchoringCheck = AnchoringCheck(postchainContext.nodeDiagnosticContext, postchainContext.blockQueriesProvider, postchainContext.appConfig)

    private lateinit var clusterManagement: ClusterManagement

    /**
     * Connect process to anchoring
     */
    @Synchronized
    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()
        localProcessChainIds[cfg.blockchainRid] = cfg.chainID
        anchoringCheck.runningChainsBlockClients[cfg.blockchainRid] = BlockQueriesAdapter(engine.getBlockQueries())

        if (cfg is GTXModuleAware && cfg is ManagedDataSourceAware) {
            // The first chain to be connected is chain0.
            // The clusterManagement instance using chain0's dataSource will be shared by all other chains.
            if (!::clusterManagement.isInitialized) {
                clusterManagement = createClusterManagement(cfg)
            }

            // create pipe manager when blockchain has anchoring STE
            getAnchorSpecialTxExtension(cfg.module)?.let {
                it.isSigner = process::isSigner
                it.clusterManagement = createClusterManagement(cfg)
                it.blockchainConfigProvider = createBlockchainConfigProvider(cfg, it.clusterManagement)
                it.anchoringConfig = AnchoringBlockchainConfigData.fromGtv(
                        cfg.rawConfig[KEY_BLOCKCHAIN_CONFIG_ANCHORING] ?: gtv(mapOf())
                )

                val blockStrategyConfig = cfg.rawConfig[KEY_BLOCKSTRATEGY] ?: gtv(mapOf())
                val gtxConfig = cfg.rawConfig[KEY_GTX] ?: gtv(mapOf())
                it.maxTxSize = min(
                        blockStrategyConfig.toObject<BaseBlockBuildingStrategyConfigurationData>().maxBlockSize,
                        gtxConfig.toObject<GtxConfigurationData>().maxTxSize
                )

                val anchorBlockQueries = engine.getBlockQueries()

                anchoringPipeManagers[cfg.chainID] = it.createPipeManager(cfg.blockchainRid) { blockchainRid ->
                    val remoteChainId = remoteProcessChainIds[blockchainRid]
                    val localChainId = localProcessChainIds[blockchainRid]
                    if (remoteChainId != null) {
                        AnchoringSubnodePipe(remoteChainId, blockchainRid, (blockchainInfrastructure as MasterBlockchainInfra).masterConnectionManager, anchorBlockQueries)
                    } else if (localChainId != null) {
                        AnchoringLocalPipe(localChainId, blockchainRid, postchainContext.blockBuilderStorage)
                    } else null // We are not running this chain currently
                }

                anchoringCheck.maybeCreateAnchoringCheckCronJob(it, cfg.blockchainRid, anchorBlockQueries, cfg.module.getQueries())
            }
        }
        anchoringPipeManagers.filterKeys { it != cfg.chainID }.values.forEach { it.onChainConnect(cfg.blockchainRid) }
    }

    /**
     *
     * Note: having more than one [AnchoringSpecialTxExtension] tied to the anchoring process would be wrong I guess, but
     * we don't care about that here.
     */
    private fun getAnchorSpecialTxExtension(module: GTXModule): AnchoringSpecialTxExtension? {
        return module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is AnchoringSpecialTxExtension)
        } as AnchoringSpecialTxExtension?
    }

    open fun createClusterManagement(configuration: ManagedDataSourceAware): ClusterManagement =
            ClusterManagementImpl { name, gtv -> configuration.dataSource.query(name, gtv) }

    open fun createBlockchainConfigProvider(configuration: ManagedDataSourceAware, clusterManagement: ClusterManagement): BlockchainConfigProvider =
            ManagedBlockchainConfigProvider(
                    NodeManagementImpl { name, gtv -> configuration.dataSource.query(name, gtv) },
                    clusterManagement
            )

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) {
        val blockchainRid = process.blockchainEngine.blockchainRid
        localProcessChainIds.remove(blockchainRid)
        anchoringCheck.remove(blockchainRid)
        anchoringCheck.runningChainsBlockClients.remove(blockchainRid)
        anchoringPipeManagers.remove(process.blockchainEngine.chainID)?.shutdown() // In case this is an anchoring chain
        anchoringPipeManagers.values.forEach { it.onChainDisconnect(blockchainRid) }
    }

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) {
        anchoringPipeManagers.values.forEach { it.afterCommit(process.blockchainEngine.blockchainRid, height) }
    }

    @Synchronized
    override fun afterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long) {
        anchoringPipeManagers.values.forEach { it.afterCommit(blockchainRid, blockHeight) }
    }

    @Synchronized
    override fun shutdown() {
    }

    override fun connectRemoteProcess(process: RemoteBlockchainProcess) {
        remoteProcessChainIds[process.blockchainRid] = process.chainId

        // Should always be true
        if (blockchainInfrastructure is MasterBlockchainInfra) {
            anchoringCheck.runningChainsBlockClients[process.blockchainRid] = MasterClient(
                    blockchainInfrastructure.masterConnectionManager.masterSubQueryManager,
                    process.blockchainRid
            )
        }
        anchoringPipeManagers.values.forEach { it.onChainConnect(process.blockchainRid) }
    }

    override fun disconnectRemoteProcess(process: RemoteBlockchainProcess) {
        remoteProcessChainIds.remove(process.blockchainRid)
        anchoringCheck.runningChainsBlockClients.remove(process.blockchainRid)
        anchoringPipeManagers.values.forEach { it.onChainDisconnect(process.blockchainRid) }
    }
}