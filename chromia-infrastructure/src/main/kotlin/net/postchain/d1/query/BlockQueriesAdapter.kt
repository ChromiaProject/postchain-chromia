package net.postchain.d1.query

import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainReadClient
import net.postchain.client.core.TxDetail
import net.postchain.core.block.BlockQueries
import net.postchain.gtv.Gtv

class BlockQueriesAdapter(private val blockQueries: BlockQueries) : PostchainReadClient {
    override fun blockAtHeightSync(height: Long): BlockDetail? {
        val blockRid = blockQueries.getBlockRid(height).get()
        return blockRid?.let {
            blockQueries.getBlock(it, true).get()?.let { block ->
                BlockDetail(
                        block.rid,
                        block.prevBlockRID,
                        block.header,
                        block.height,
                        block.transactions.map { tx ->
                            TxDetail(
                                    tx.rid,
                                    tx.hash,
                                    tx.data
                            )
                        },
                        block.witness,
                        block.timestamp
                )
            }
        }
    }

    override fun currentBlockHeightSync() = blockQueries.getBestHeight().get()

    override fun querySync(name: String, gtv: Gtv) = blockQueries.query(name, gtv).get()
}