package net.postchain.mc.cli.chromia0

import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.ClientConfig

class CliExecutionC0(config: ClientConfig) : CliExecution(config) {

    private fun addBc(nodes: String, data: ByteArray): GTXTransactionBuilder {
        val nodeList = nodes.split(",").map {
            getPostchainClient().query("get_node",
                    gtv("pubkey" to gtv(it.hexStringToByteArray()))).get()
        }
        return makeTransactionWithNop().apply {
            addOperation(
                    "add_blockchain",
                    gtv(data), gtv(nodeList))
            sign(buildSigMaker())
        }
    }

    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(stopBlockchainInternal(blockchainRID, removeReplicas), "Blockchain has been stopped",
                "Cannot stop blockchain")
    }

    fun stopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean): GTXTransactionBuilder {
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation(
                    "stop_blockchain",
                    blockchain, gtv(removeReplicas))
            sign(buildSigMaker())
        }
    }

}