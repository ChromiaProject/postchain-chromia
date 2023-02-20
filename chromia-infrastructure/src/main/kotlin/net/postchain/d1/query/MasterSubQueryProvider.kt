package net.postchain.d1.query

import net.postchain.client.core.PostchainBlockClient
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.subnode.SubConnectionManager

class MasterSubQueryProvider(
        private val myBlockchainRid: BlockchainRid,
        private val myChainId: Long,
        private val subConnectionManager: SubConnectionManager,
        private val clusterManagement: ClusterManagement,
        private val directoryDataSource: DirectoryDataSource,
        private val blockQueriesProvider: BlockQueriesProvider
) : ChromiaQueryProvider {

    override fun getChain0Query(): PostchainQuery = Chain0MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager)

    override fun getAnchorQuery(): PostchainBlockClient {
        val cluster = clusterManagement.getClusterOfBlockchain(myBlockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager, info.anchoringChain)
    }

    override fun getQuery(targetBlockchainRid: BlockchainRid): PostchainBlockClient? {
        val thisContainer = directoryDataSource.getContainerForBlockchain(myBlockchainRid)
        val chainContainer = directoryDataSource.getContainerForBlockchain(targetBlockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(targetBlockchainRid)?.let {
                BlockQueriesAdapter(it)
            }
        } else {
            MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager, targetBlockchainRid)
        }
    }
}
