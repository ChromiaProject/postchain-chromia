package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.managed.ManagedNodeDataSource

class LocalQueryProvider(
        private val blockchainRid: BlockchainRid,
        private val blockQueriesProvider: BlockQueriesProvider,
        private val clusterManagement: ClusterManagement,
        private val managedNodeDataSource: ManagedNodeDataSource
) : ChromiaQueryProvider {
    override fun getChain0Query(): PostchainQuery {
        return object : PostchainQuery {
            override fun querySync(name: String, gtv: Gtv): Gtv = managedNodeDataSource.query(name, gtv)
        }
    }

    override fun getAnchorQuery(): PostchainQuery? {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return getQuery(info.anchoringChain)
    }

    override fun getQuery(blockchainRid: BlockchainRid): PostchainQuery? =
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                object : PostchainQuery {
                    override fun querySync(name: String, gtv: Gtv) = it.query(name, gtv).get()
                }
            }
}