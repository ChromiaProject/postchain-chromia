package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.client.core.PostchainQuery
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.core.RemoteBlockchainProcess
import net.postchain.core.RemoteBlockchainProcessConnectable
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import net.postchain.managed.config.ManagedDataSourceAware

open class AnchorProcessManagerExtension(
    postchainContext: PostchainContext
) : BlockchainProcessManagerExtension, RemoteBlockchainProcessConnectable {

    private val localDispatcher = ClusterAnchorDispatcher(postchainContext.storage)

    /**
     * Connect process to ICMF:
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
                localDispatcher.connectReceiver(cfg.chainID, it.icmfReceiver)
                it.clusterManagement = createClusterManagement(cfg)
            }

            // connect process to local dispatcher
            localDispatcher.connectChain(cfg.chainID)
        }
    }

    /**
     *
     * Note: having more than one [AnchorSpecialTxExtension] tied to the Anchor process would be wrong I guess, but
     * we don't care about that here.
     */
    private fun getAnchorSpecialTxExtension(module: GTXModule): AnchorSpecialTxExtension? {
        return module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is AnchorSpecialTxExtension)
        } as AnchorSpecialTxExtension?
    }

    open fun createClusterManagement(configuration: ManagedDataSourceAware): ClusterManagement =
        ClusterManagementImpl(object : PostchainQuery {
            override fun querySync(name: String, gtv: Gtv): Gtv = configuration.dataSource.query(name, gtv)
        })

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
    override fun shutdown() {
    }

    override fun connectRemoteProcess(process: RemoteBlockchainProcess) {
        localDispatcher.connectSubnodeChain(
            process.chainId, process.blockchainRid, process.restApiUrl
        )
    }

    override fun disconnectRemoteProcess(process: RemoteBlockchainProcess) {
        localDispatcher.disconnectSubnodeChain(process.chainId)
    }
}
