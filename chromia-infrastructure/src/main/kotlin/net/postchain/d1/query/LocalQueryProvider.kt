package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.ManagedNodeDataSource

class LocalQueryProvider(
        private val blockchainRid: BlockchainRid,
        private val blockQueriesProvider: BlockQueriesProvider,
        private val clusterManagement: ClusterManagement,
        private val managedNodeDataSource: ManagedNodeDataSource
) : ChromiaQueryProvider {
    override fun getChain0Query()= PostchainQuery { name, gtv -> managedNodeDataSource.query(name, gtv) }

    override fun getAnchorQuery(): PostchainBlockClient? {
        val cluster = clusterManagement.getClusterOfBlockchain(blockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return getQuery(info.anchoringChain)
    }

    override fun getQuery(blockchainRid: BlockchainRid): PostchainBlockClient? =
            blockQueriesProvider.getBlockQueries(blockchainRid)?.let {
                BlockQueriesAdapter(it)
            }
}