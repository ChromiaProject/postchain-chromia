package net.postchain.d1.query

import net.postchain.client.core.TxDetail

fun transformBlockDetail(blockDetail: net.postchain.core.block.BlockDetail) =
        net.postchain.client.core.BlockDetail(
                blockDetail.rid,
                blockDetail.prevBlockRID,
                blockDetail.header,
                blockDetail.height,
                blockDetail.transactions.map { tx ->
                    TxDetail(
                            tx.rid,
                            tx.hash,
                            tx.data
                    )
                },
                blockDetail.witness,
                blockDetail.timestamp
        )
