package net.postchain.d1.icmf

import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.Gtv

object QueryProviderMocks : ChromiaQueryProvider {
    private val mockQueries = mutableMapOf<BlockchainRid, (String, Gtv) -> Gtv>()

    var chain0Queries: ((String, Gtv) -> Gtv)? = null

    var anchorQueries: ((String, Gtv) -> Gtv)? = null

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

    override fun getAnchorQuery(): PostchainQuery? = anchorQueries?.let {
        object : PostchainQuery {
            override fun querySync(name: String, gtv: Gtv) = it(name, gtv)
        }
    }

    override fun getQuery(blockchainRid: BlockchainRid): PostchainQuery? = mockQueries[blockchainRid]?.let {
        object : PostchainQuery {
            override fun querySync(name: String, gtv: Gtv) = it(name, gtv)
        }
    }
}
