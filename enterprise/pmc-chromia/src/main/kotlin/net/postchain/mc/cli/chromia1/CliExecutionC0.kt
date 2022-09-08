package net.postchain.mc.cli.chromia1

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.common0.CliExecution
import java.io.File

class CliExecutionC0(config: PostchainClientConfig) : CliExecution(config) {

    /**
     * format: Format of blockchain configuration file. Can be either xml or gtv
     */
    fun addBlockchain(blockchainConfigFile: String, nodes: String, format: String?) {
        sendTxSync(
            addBlockchainInternal(File(blockchainConfigFile), nodes, format), "Blockchain has been added",
            "Cannot add blockchain"
        )
    }

    fun addBlockchainInternal(blockchainConfigFile: File, nodes: String, format: String?): TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return addBc(nodes, data)
    }

    fun addBlockchainGtvInternal(blockchainConfig: Gtv, nodes: String): TransactionBuilder {
        val data = GtvEncoder.encodeGtv(blockchainConfig)
        return addBc(nodes, data)
    }

    private fun addBc(nodes: String, data: ByteArray): TransactionBuilder {
        val nodeList = nodes.split(",").map {
            getPostchainClient().querySync(
                "get_node",
                gtv("pubkey" to gtv(it.hexStringToByteArray()))
            )
        }
        return makeTransactionWithNop().addOperation(
                "add_blockchain",
                gtv(data), gtv(nodeList)
            )
    }

    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(
            stopBlockchainInternal(blockchainRID, removeReplicas), "Blockchain has been stopped",
            "Cannot stop blockchain"
        )
    }

    fun stopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean): TransactionBuilder {
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().addOperation(
                "stop_blockchain",
                blockchain, gtv(removeReplicas)
            )
    }

    fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?) {
        sendTxSync(
            addConfigurationInternal(blockchainRID, File(blockchainConfigFile), height, format),
            "blockchain configuration was added successfully!", "Cannot add blockchain configuration"
        )
    }

    fun addConfigurationInternal(blockchainRID: String, blockchainConfigFile: File, height: Long, format: String?):
            TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().addOperation(
                "add_configuration",
                blockchain, gtv(data), gtv(height)
            )
    }

    fun addBlockchainSigners(blockchainRID: String, signers: String, heightDelay: Long) {
        sendTxSync(
            addBlockchainSignersInternal(blockchainRID, signers, heightDelay),
            "Blockchain's signers have been added", "Cannot add signers"
        )
    }


    fun removeBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(
            removeBlockchainSignersInternal(blockchainRID, signers),
            "Blockchain's signers have been removed", "Cannot remove blockchain's signers"
        )
    }

    fun removeBlockchainSignersInternal(blockchainRID: String, signers: String): TransactionBuilder {
        val nodeList = signers.split(",").map { nodeGtv(it) }
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().addOperation("remove_blockchain_signers", blockchain, gtv(nodeList))
    }

    fun runTxAndAwaitConfirmed(doer: TransactionBuilder) {
        doInTryBlock {
            val txResult = doer.postSyncAwaitConfirmation()
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Blockchain's signers have been removed")
            } else {
                throw CliError.Companion.CliException("Cannot remove blockchain's signers")
            }
        }
    }

    fun registerProvider(key: String) {
        sendTxSync(
            registerProviderInternal(key), "Provider has been registered",
            "Cannot register provider"
        )
    }

    fun updateProvider(key: String, name: String, beneficiary: String) {
        sendTxSync(
            updateProviderInternal(key, name, beneficiary), "Provider data has been updated",
            "Cannot update provider"
        )
    }

    fun updateProviderInternal(key: String, name: String, beneficiary: String): TransactionBuilder {
        val provider = providerGtv(key)
        var data: Array<Gtv> = arrayOf(provider)
        if (name.isNotEmpty()) {
            data = data.plus(gtv(name))
        } else {
            data = data.plus(GtvNull)
        }
        if (beneficiary.isNotEmpty()) {
            data = data.plus(gtv(beneficiary))
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().addOperation("update_provider_data", *data)
    }

    fun enableProvider(key: String) {
        sendTxSync(enableProviderInternal(key), "Provider has been enabled", "Cannot enable provider")
    }

    fun disableProvider(key: String) {
        sendTxSync(disableProviderInternal(key), "Provider has been disabled", "Cannot disable provider")
    }
}