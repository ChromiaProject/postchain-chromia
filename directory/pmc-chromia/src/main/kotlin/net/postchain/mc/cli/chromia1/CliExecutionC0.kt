package net.postchain.mc.cli.chromia1

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.mc.cli.common0.CliExecution

class CliExecutionC0(config: PostchainClientConfig) : CliExecution(config) {

    private fun addBc(nodes: String, data: ByteArray): TransactionBuilder {
        val nodeList = nodes.split(",").map {
            getPostchainClient().querySync("get_node",
                    gtv("pubkey" to gtv(it.hexStringToByteArray())))
        }
        return makeTransactionWithNop().addOperation(
                    "add_blockchain",
                    gtv(data), gtv(nodeList))
    }

    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(stopBlockchainInternal(blockchainRID, removeReplicas), "Blockchain has been stopped",
                "Cannot stop blockchain")
    }

    fun stopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean): TransactionBuilder {
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().addOperation(
                    "stop_blockchain",
                    blockchain, gtv(removeReplicas))
    }

}