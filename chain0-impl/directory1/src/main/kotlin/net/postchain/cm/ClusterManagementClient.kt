package net.postchain.cm

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TxRid
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtx.Gtx
import java.time.Duration

class ClusterManagementClient(val queryFunction: (String, Gtv) -> Gtv): PostchainClient {
    override fun querySync(name: String, gtv: Gtv) = queryFunction(name, gtv)

    override val config: PostchainClientConfig get() = TODO("Not yet implemented")
    override fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Duration) = TODO("Not yet implemented")
    override fun blockAtHeight(height: Long) = TODO("Not yet implemented")
    override fun blockAtHeightSync(height: Long) = TODO("Not yet implemented")
    override fun checkTxStatus(txRid: TxRid) = TODO("Not yet implemented")
    override fun currentBlockHeight() = TODO("Not yet implemented")
    override fun currentBlockHeightSync() = TODO("Not yet implemented")
    override fun postTransaction(tx: Gtx) = TODO("Not yet implemented")
    override fun postTransactionSync(tx: Gtx) = TODO("Not yet implemented")
    override fun postTransactionSyncAwaitConfirmation(tx: Gtx) = TODO("Not yet implemented")
    override fun query(name: String, gtv: Gtv) =TODO("Not yet implemented")
    override fun transactionBuilder() = TODO("Not yet implemented")
    override fun transactionBuilder(signers: List<KeyPair>) = TODO("Not yet implemented")
}
