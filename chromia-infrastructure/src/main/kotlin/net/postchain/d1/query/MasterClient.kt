package net.postchain.d1.query

import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.network.mastersub.MasterSubQueryManager

class MasterClient(
        private val myBlockchainRid: BlockchainRid,
        private val myChainId: Long,
        private val queryManager: MasterSubQueryManager,
        private val targetBlockchainRid: BlockchainRid
) : PostchainBlockClient {
    override fun blockAtHeight(height: Long): BlockDetail? {
        return queryManager.blockAtHeight(
                myChainId,
                myBlockchainRid,
                targetBlockchainRid,
                height
        ).toCompletableFuture().get()?.let(::transformBlockDetail)
    }

    override fun currentBlockHeight(): Long {
        throw NotImplementedError("Not yet implemented")
    }

    override fun query(name: String, args: Gtv): Gtv = queryManager.query(
            myChainId,
            myBlockchainRid,
            targetBlockchainRid,
            name,
            args
    ).toCompletableFuture().get()

}
