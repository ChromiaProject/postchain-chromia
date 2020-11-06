package net.postchain.mc.cli.chromia0

import net.postchain.client.core.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.gtv.*
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class CliExecutionC0(config: ClientConfig) : CliExecution(config) {

//    companion object : KLogging()

    /**
     * format: Format of blockchain configuration file
     */
    fun addBlockchain(blockchainConfigFile: String, nodes: String, format: String?) {
        sendTxSync(addBlockchainInternal(blockchainConfigFile, nodes, format), "Blockchain has been added", "Cannot add blockchain")
    }

    fun addBlockchainInternal(blockchainConfigFile: String, nodes: String, format: String?): GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        val nodeList = nodes.split(",").map { getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
        return makeTransactionWithNop().apply {
            addOperation("add_blockchain",
                    arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }

    /**
     *
     */
    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(stopBlockchainInternal(blockchainRID, removeReplicas), "Blockchain has been stopped", "Cannot stop blockchain")
    }

    fun stopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean): GTXTransactionBuilder {
        val blockchain = getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("stop_blockchain",
                    arrayOf(blockchain, GtvFactory.gtv(removeReplicas)))
            sign(buildSigMaker())
        }
    }

    /**
     *
     */
    fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?) {
        sendTxSync(addConfigurationInternal(blockchainRID, blockchainConfigFile, height, format),
                "blockchain configuration was added successfully!", "Cannot add blockchain configuration")
        doInTryBlock {
            val tx = addConfigurationInternal(blockchainConfigFile, blockchainRID, height, format)
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain configuration was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain configuration")
            }
        }
    }

    fun addConfigurationInternal(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?): GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        val blockchain = getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("add_configuration",
                    arrayOf(blockchain, GtvFactory.gtv(data), GtvFactory.gtv(height)))
            sign(buildSigMaker())
        }
    }

    /**
     *
     */
    fun addNode(key: String, host: String, port: Long) {
        sendTxSync(addNodeInternal(key, host, port), "Node has been enabled", "Cannot add node")
    }

    /**
     *
     */
    fun addReplica(blockchainRID: String, key: String) {
        sendTxSync(addReplicaInternal(blockchainRID, key), "Replica added", "Cannot add replica node")
    }

    /**
     *
     */
    fun removeReplica(blockchainRID: String, key: String) {
        sendTxSync(removeReplicaInternal(blockchainRID, key), "Replica removed", "Cannot remove replica node")
    }


    /**
     *
     */
    fun removeNode(key: String) {
        sendTxSync(removeNodeInternal(key), "Node removed", "Cannot remove node")
    }


    /**
     *
     */
    fun addBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(addBlockchainSignersInternal(blockchainRID, signers),
                "Blockchain's signers have been added", "Cannot add signers")
    }


    /**
     *
     */
    fun removeBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(removeBlockchainSignersInternal(blockchainRID, signers),
                "Blockchain's signers have been removed", "Cannot remove blockchain's signers")
    }

    fun removeBlockchainSignersInternal(blockchainRID: String, signers: String): GTXTransactionBuilder {
        val nodeList = signers.split(",").map {
            getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
        }
        val blockchain = getPostchainClient().query("get_blockchain", GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("remove_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }


    fun runTxAndAwaitConfirmed(doer: GTXTransactionBuilder) {
        doInTryBlock {
            val txResult = doer.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Blockchain's signers have been removed")
            } else {
                throw CliError.Companion.CliException("Cannot remove blockchain's signers")
            }
        }
    }

    /**
     *
     */
    fun registerProvider(key: String) {
        sendTxSync(registerProviderInternal(key), "Provider has been registered", "Cannot register provider")
    }

    /**
     *
     */
    fun updateProvider(key: String, name: String, beneficiary: String) {
        sendTxSync(updateProviderInternal(key, name, beneficiary), "Provider data has been updated", "Cannot update provider")
    }
    fun updateProviderInternal(key: String, name: String, beneficiary: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
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

    /**
     *
     */
    fun enableProvider(key: String) {
        sendTxSync(enableProviderInternal(key), "Provider has been enabled", "Cannot enable provider")
    }

    /**
     *
     */
    fun disableProvider(key: String) {
        sendTxSync(disableProviderInternal(key), "Provider has been disabled", "Cannot disable provider")
    }
}