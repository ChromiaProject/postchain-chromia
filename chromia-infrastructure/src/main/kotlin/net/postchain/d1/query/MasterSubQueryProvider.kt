package net.postchain.d1.query

import net.postchain.chromia.nm_api.nmGetContainerForBlockchain
import net.postchain.client.core.PostchainBlockClient
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.network.mastersub.subnode.SubConnectionManager

class MasterSubQueryProvider(
        private val myBlockchainRid: BlockchainRid,
        private val myChainId: Long,
        private val subConnectionManager: SubConnectionManager,
        private val clusterManagement: ClusterManagement,
        private val blockQueriesProvider: BlockQueriesProvider
) : ChromiaQueryProvider {

    override fun getChain0Query(): PostchainQuery = Chain0MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager)

    override fun getSystemAnchoringQuery(): PostchainBlockClient? {
        return clusterManagement.getSystemAnchoringChain()?.let {
            MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager, it)
        }
    }

    override fun getClusterAnchoringQuery(): PostchainBlockClient {
        val cluster = clusterManagement.getClusterOfBlockchain(myBlockchainRid)
        val info = clusterManagement.getClusterInfo(cluster)
        return MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager, info.anchoringChain)
    }

    override fun getQuery(targetBlockchainRid: BlockchainRid): PostchainBlockClient? {
        val chainQuery = getChain0Query()
        val thisContainer = chainQuery.nmGetContainerForBlockchain(myBlockchainRid)
        val chainContainer = chainQuery.nmGetContainerForBlockchain(targetBlockchainRid)
        return if (thisContainer == chainContainer) {
            blockQueriesProvider.getBlockQueries(targetBlockchainRid)?.let {
                BlockQueriesAdapter(it)
            }
        } else {
            MasterClient(myBlockchainRid, myChainId, subConnectionManager.masterSubQueryManager, targetBlockchainRid)
        }
    }
}
