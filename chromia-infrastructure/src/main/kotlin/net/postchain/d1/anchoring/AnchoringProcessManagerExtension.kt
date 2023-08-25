package net.postchain.d1.anchoring

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY
import net.postchain.base.configuration.KEY_GTX
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.bpm.ContainerBlockchainProcessManagerExtension
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcess
import net.postchain.core.RemoteBlockchainProcess
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.config.ManagedBlockchainConfigProvider
import net.postchain.d1.nm_api.NodeManagementImpl
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GtxConfigurationData
import net.postchain.managed.config.ManagedDataSourceAware
import kotlin.math.min

open class AnchoringProcessManagerExtension(
        private val postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure
) : ContainerBlockchainProcessManagerExtension, RemoteBlockchainProcessConnectable {

    private val localDispatcher = AnchoringDispatcher(postchainContext.blockBuilderStorage, blockchainInfrastructure)
    private val remoteProcessChainIds = mutableMapOf<BlockchainRid, Long>()

    /**
     * Connect process to cluster anchoring:
     * 1. register receiver chain if necessary
     * 2. connect process to local dispatcher
     */
    @Synchronized
    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()

        if (cfg is GTXModuleAware && cfg is ManagedDataSourceAware) {
            // create receiver when blockchain has anchoring STE
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

                it.createReceiver(cfg.blockchainRid)
                localDispatcher.connectReceiver(cfg.chainID, it.anchoringReceiver)

                (engine.getBlockBuildingStrategy() as? AnchoringBlockBuildingStrategy)?.apply {
                    txExtension = it
                    anchoringConfig = it.anchoringConfig
                }
            }

            // connect process to local dispatcher
            localDispatcher.connectChain(cfg.chainID)
        }
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
                    clusterManagement,
                    postchainContext.appConfig
            )

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) {
        localDispatcher.disconnectChain(
                process.blockchainEngine.getConfiguration().chainID
        )
    }

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) {
        localDispatcher.afterCommit(
                process.blockchainEngine.getConfiguration().chainID,
                height
        )
    }

    @Synchronized
    override fun afterCommitInSubnode(blockchainRid: BlockchainRid, blockHeight: Long) {
        val chainId = remoteProcessChainIds[blockchainRid]
                ?: throw ProgrammerMistake("Received commit from blockchain with rid ${blockchainRid.toHex()} that has no mapped chain id")
        localDispatcher.afterCommit(chainId, blockHeight)
    }

    @Synchronized
    override fun shutdown() {}

    override fun connectRemoteProcess(process: RemoteBlockchainProcess) {
        remoteProcessChainIds[process.blockchainRid] = process.chainId
        localDispatcher.connectSubnodeChain(process.chainId, process.blockchainRid)
    }

    override fun disconnectRemoteProcess(process: RemoteBlockchainProcess) {
        localDispatcher.disconnectSubnodeChain(process.chainId)
        remoteProcessChainIds.remove(process.blockchainRid)
    }
}
