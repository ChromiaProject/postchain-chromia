package net.postchain.d1.query

import net.postchain.base.data.DatabaseAccess
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.network.mastersub.MasterSubQueryManager

class MasterClient(
        private val queryManager: MasterSubQueryManager,
        private val targetBlockchainRid: BlockchainRid
) : PostchainBlockClient {

    override fun blockAtHeight(height: Long): BlockDetail? {
        return queryManager.blockAtHeight(
                targetBlockchainRid,
                height
        ).toCompletableFuture().get()?.let(::transformBlockDetail)
    }

    fun blocksFromHeight(fromHeight: Long, limit: Long): List<DatabaseAccess.BlockInfoExt> {
        return queryManager.blocksFromHeight(
                targetBlockchainRid,
                fromHeight,
                limit
        ).toCompletableFuture().get() ?: listOf()
    }

    override fun query(name: String, args: Gtv): Gtv = queryManager.query(
            targetBlockchainRid,
            name,
            args
    ).toCompletableFuture().get()
}
