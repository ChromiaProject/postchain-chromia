package net.postchain.d1.containers

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.managed.DirectoryDataSource

class DefaultChromiaQueryProvider(
        private val blockchainRid: BlockchainRid,
        private val appConfig: AppConfig,
        private val clusterManagement: ClusterManagement,
        private val directoryDataSource: DirectoryDataSource,
        private val blockQueriesProvider: BlockQueriesProvider
) : ChromiaQueryProvider {

    override fun getChain0Query(): (String, Gtv) -> Gtv {
        val client = MasterClientProvider.getChain0Client(appConfig)
        return client::querySync
    }

    override fun getAnchorQuery(): ((String, Gtv) -> Gtv)? {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        val client = MasterClientProvider.getClient(appConfig, info.anchoringChain)
        return client::querySync
    }

    override fun getQuery(blockchainRid: BlockchainRid): ((String, Gtv) -> Gtv)? {
        val thisContainer = directoryDataSource.getContainerForBlockchain(this.blockchainRid)
        val chainContainer = directoryDataSource.getContainerForBlockchain(blockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                { name, args -> it.query(name, args).get() }
            }
        } else {
            MasterClientProvider.getClient(appConfig, blockchainRid)::querySync
        }
    }
}