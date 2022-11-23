package net.postchain.d1.icmf

import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainQuery
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.Gtv

object QueryProviderMocks : ChromiaQueryProvider {
    private val mockQueries = mutableMapOf<BlockchainRid, (String, Gtv) -> Gtv>()

    var chain0Queries: PostchainQuery? = null

    var anchorQueries: PostchainBlockClient? = null

    fun addMockQueries(blockchainRid: BlockchainRid, query: (String, Gtv) -> Gtv) {
        mockQueries[blockchainRid] = query
    }

    fun clearMocks() {
        mockQueries.clear()
        chain0Queries = null
        anchorQueries = null
    }

    override fun getChain0Query()= chain0Queries!!

    override fun getAnchorQuery(): PostchainBlockClient? = anchorQueries

    override fun getQuery(blockchainRid: BlockchainRid): PostchainBlockClient? = mockQueries[blockchainRid]?.let {
        object : PostchainBlockClient {
            override fun blockAtHeight(height: Long): BlockDetail? {
                throw NotImplementedError()
            }

            override fun currentBlockHeight(): Long {
                throw NotImplementedError()
            }

            override fun query(name: String, gtv: Gtv) = it(name, gtv)
        }
    }
}
