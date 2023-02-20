package net.postchain.d1.icmf

import net.postchain.client.core.PostchainBlockClient
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.d1.query.ChromiaQueryProvider

object QueryProviderMocks : ChromiaQueryProvider {
    private val mockQueries = mutableMapOf<BlockchainRid, PostchainBlockClient>()

    var chain0Queries: PostchainQuery? = null

    var anchorQueries: PostchainBlockClient? = null

    fun addMockQueries(blockchainRid: BlockchainRid, query: PostchainBlockClient) {
        mockQueries[blockchainRid] = query
    }

    fun clearMocks() {
        mockQueries.clear()
        chain0Queries = null
        anchorQueries = null
    }

    override fun getChain0Query() = chain0Queries!!

    override fun getAnchorQuery(): PostchainBlockClient? = anchorQueries

    override fun getQuery(targetBlockchainRid: BlockchainRid): PostchainBlockClient? = mockQueries[targetBlockchainRid]
}
