package net.postchain.d1.query

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.query.QueryRunner

class DefaultChromiaQueryProvider(
        private val blockchainRid: BlockchainRid,
        private val appConfig: AppConfig,
        private val clusterManagement: ClusterManagement,
        private val directoryDataSource: DirectoryDataSource,
        private val blockQueriesProvider: BlockQueriesProvider
) : ChromiaQueryProvider {

    override fun getChain0Query(): QueryRunner {
        val client = MasterClientProvider.getChain0Client(appConfig)
        return QueryRunner(client::querySync)
    }

    override fun getAnchorQuery(): QueryRunner? {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        val client = MasterClientProvider.getClient(appConfig, info.anchoringChain)
        return QueryRunner(client::querySync)
    }

    override fun getQuery(blockchainRid: BlockchainRid): QueryRunner? {
        val thisContainer = directoryDataSource.getContainerForBlockchain(this.blockchainRid)
        val chainContainer = directoryDataSource.getContainerForBlockchain(blockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                QueryRunner { name, args -> it.query(name, args).get() }
            }
        } else {
            QueryRunner(
                    MasterClientProvider.getClient(appConfig, blockchainRid)::querySync
            )
        }
    }
}