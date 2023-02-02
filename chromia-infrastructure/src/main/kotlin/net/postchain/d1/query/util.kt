package net.postchain.d1.query

import net.postchain.client.core.TxDetail
import net.postchain.common.wrap

fun transformBlockDetail(blockDetail: net.postchain.core.block.BlockDetail) =
        net.postchain.client.core.BlockDetail(
                blockDetail.rid.wrap(),
                blockDetail.prevBlockRID.wrap(),
                blockDetail.header.wrap(),
                blockDetail.height,
                blockDetail.transactions.map { tx ->
                    TxDetail(
                            tx.rid.wrap(),
                            tx.hash.wrap(),
                            tx.data?.wrap()
                    )
                },
                blockDetail.witness.wrap(),
                blockDetail.timestamp
        )
