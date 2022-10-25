package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
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

    override fun getChain0Query(): PostchainQuery = MasterQueryProvider.getChain0Client(appConfig)

    override fun getAnchorQuery(): PostchainQuery {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return MasterQueryProvider.getClient(appConfig, info.anchoringChain)
    }

    override fun getQuery(blockchainRid: BlockchainRid): PostchainQuery? {
        val thisContainer = directoryDataSource.getContainerForBlockchain(this.blockchainRid)
        val chainContainer = directoryDataSource.getContainerForBlockchain(blockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                object : PostchainQuery {
                    override fun querySync(name: String, gtv: Gtv) = it.query(name, gtv).get()
                }
            }
        } else {
            MasterQueryProvider.getClient(appConfig, blockchainRid)
        }
    }
}
