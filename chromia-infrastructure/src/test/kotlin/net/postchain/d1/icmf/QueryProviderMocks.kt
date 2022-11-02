package net.postchain.d1.icmf

import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainQuery
import net.postchain.client.core.PostchainReadClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.Gtv

object QueryProviderMocks : ChromiaQueryProvider {
    private val mockQueries = mutableMapOf<BlockchainRid, (String, Gtv) -> Gtv>()

    var chain0Queries: ((String, Gtv) -> Gtv)? = null

    var anchorQueries: PostchainReadClient? = null

    fun addMockQueries(blockchainRid: BlockchainRid, query: (String, Gtv) -> Gtv) {
        mockQueries[blockchainRid] = query
    }

    fun clearMocks() {
        mockQueries.clear()
        chain0Queries = null
        anchorQueries = null
    }

    override fun getChain0Query(): PostchainQuery = object : PostchainQuery {
        override fun querySync(name: String, gtv: Gtv) = chain0Queries!!(name, gtv)
    }

    override fun getAnchorQuery(): PostchainReadClient? = anchorQueries

    override fun getQuery(blockchainRid: BlockchainRid): PostchainReadClient? = mockQueries[blockchainRid]?.let {
        object : PostchainReadClient {
            override fun blockAtHeightSync(height: Long): BlockDetail? {
                throw NotImplementedError()
            }

            override fun currentBlockHeightSync(): Long {
                throw NotImplementedError()
            }

            override fun querySync(name: String, gtv: Gtv) = it(name, gtv)
        }
    }
}
