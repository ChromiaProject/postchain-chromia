package net.postchain.mc.cli.chromia0

import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.ClientConfig

class CliExecutionC0(config: ClientConfig) : CliExecution(config) {

    private fun addBc(nodes: String, data: ByteArray): GTXTransactionBuilder {
        val nodeList = nodes.split(",").map {
            getPostchainClient().query("get_node",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
        }
        return makeTransactionWithNop().apply {
            addOperation("add_blockchain",
                    arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
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
            addOperation("stop_blockchain",
                    arrayOf(blockchain, GtvFactory.gtv(removeReplicas)))
            sign(buildSigMaker())
        }
    }

    fun updateProvider(key: String, name: String, beneficiary: String) {
        sendTxSync(updateProviderAsync(key, name, beneficiary), "Provider data has been updated",
                "Cannot update provider")
    }

    fun updateProviderAsync(key: String, name: String, beneficiary: String): GTXTransactionBuilder {
        val provider = providerGtv(key)
        var data: Array<Gtv> = arrayOf(provider)
        if (name.isNotEmpty()) {
            data = data.plus(GtvFactory.gtv(name))
        } else {
            data = data.plus(GtvNull)
        }
        if (beneficiary.isNotEmpty()) {
            data = data.plus(GtvFactory.gtv(beneficiary))
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().apply {
            addOperation("update_provider_data", data)
            sign(buildSigMaker())
        }
    }

}