package net.postchain.d1

import net.postchain.client.core.PostchainBlockClient
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.d1.query.ChromiaQueryProvider

object QueryProviderMocks : ChromiaQueryProvider {
    private val mockQueries = mutableMapOf<BlockchainRid, PostchainBlockClient>()

    var chain0Queries: PostchainQuery? = null

    var clusterAnchoringQueries: PostchainBlockClient? = null

    var systemAnchoringQueries: PostchainBlockClient? = null

    fun addMockQueries(blockchainRid: BlockchainRid, query: PostchainBlockClient) {
        mockQueries[blockchainRid] = query
    }

    fun clearMocks() {
        mockQueries.clear()
        chain0Queries = null
        clusterAnchoringQueries = null
    }

    override fun getChain0Query() = chain0Queries!!

    override fun getSystemAnchoringQuery() = systemAnchoringQueries

    override fun getClusterAnchoringQuery(): PostchainBlockClient? = clusterAnchoringQueries

    override fun getQuery(targetBlockchainRid: BlockchainRid): PostchainBlockClient? = mockQueries[targetBlockchainRid]
}
