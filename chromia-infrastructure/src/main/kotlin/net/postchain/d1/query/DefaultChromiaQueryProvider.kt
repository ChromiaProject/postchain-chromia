package net.postchain.d1.query

import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.DirectoryDataSource

class DefaultChromiaQueryProvider(
        private val blockchainRid: BlockchainRid,
        private val appConfig: AppConfig,
        private val clusterManagement: ClusterManagement,
        private val directoryDataSource: DirectoryDataSource,
        private val blockQueriesProvider: BlockQueriesProvider
) : ChromiaQueryProvider {

    override fun getChain0Query(): PostchainBlockClient = MasterQueryProvider.getChain0Client(appConfig)

    override fun getAnchorQuery(): PostchainBlockClient {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return MasterQueryProvider.getClient(appConfig, info.anchoringChain)
    }

    override fun getQuery(blockchainRid: BlockchainRid): PostchainBlockClient? {
        val thisContainer = directoryDataSource.getContainerForBlockchain(this.blockchainRid)
        val chainContainer = directoryDataSource.getContainerForBlockchain(blockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                BlockQueriesAdapter(it)
            }
        } else {
            MasterQueryProvider.getClient(appConfig, blockchainRid)
        }
    }
}
