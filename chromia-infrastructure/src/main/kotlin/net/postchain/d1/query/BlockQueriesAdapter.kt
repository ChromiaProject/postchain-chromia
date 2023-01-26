package net.postchain.d1.query

import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainBlockClient
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.Gtv

class BlockQueriesAdapter(private val blockQueries: BlockQueries) : PostchainBlockClient {
    override fun blockAtHeight(height: Long): BlockDetail? {
        val blockRid = blockQueries.getBlockRid(height).get()
        return blockRid?.let {
            blockQueries.getBlock(it, true).get()?.let(::transformBlockDetail)
        }
    }

    override fun currentBlockHeight() = blockQueries.getBestHeight().get()

    override fun query(name: String, args: Gtv) = blockQueries.query(name, args).get()
}
