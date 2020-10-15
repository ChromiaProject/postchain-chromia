package net.postchain.mc.cli.chromia0

import mu.KLogging
import net.postchain.base.validateMerklePath
import net.postchain.client.core.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import java.io.File

class CliExecution(config: ClientConfig): CliExecution(config) {

//    companion object : KLogging()

    /**
     * format: Format of blockchain configuration file
     */
    override fun addBlockchain(blockchainConfigFile: String, nodes: String, format: String?) {
        doInTryBlock {
            val data = readConfigurationFile(blockchainConfigFile, format)
            val nodeList = nodes.split(",").map { getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
            val tx = makeTransactionWithNop().apply {
                addOperation("add_blockchain",
                        arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
                sign(buildSigMaker())
            }

            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain")
            }
        } 
    }

    /**
     *
     */
    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        doInTryBlock {
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("stop_blockchain",
                        arrayOf(blockchain, GtvFactory.gtv(removeReplicas)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain was stop successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot stop blockchain")
            }
        } 
    }

    /**
     *
     */
    override fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?) {
        doInTryBlock {
            val data = readConfigurationFile(blockchainConfigFile, format)
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("add_configuration",
                        arrayOf(blockchain, GtvFactory.gtv(data), GtvFactory.gtv(height)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain configuration was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain configuration")
            }
        } 
    }

    /**
     *
     */
    override fun addNode(key: String, host: String, port: Long) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                        "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("add_node",
                        arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray()), GtvFactory.gtv(host), GtvFactory.gtv(port)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Node had been added successfully")
            } else {
                throw CliError.Companion.CliException("Cannot add node")
            }
        } 
    }

    /**
     *
     */
    override fun addReplica(blockchainRID: String, key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val node = getPostchainClient().query("get_node",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("add_replica", arrayOf(provider, blockchain, node))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Replica node had been added successfully")
            } else {
                throw CliError.Companion.CliException("Cannot add replica node")
            }
        } 
    }

    /**
     *
     */
    fun removeReplica(blockchainRID: String, key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val node = getPostchainClient().query("get_node",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("remove_replica", arrayOf(provider, blockchain, node))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Replica node had been removed successfully")
            } else {
                throw CliError.Companion.CliException("Cannot remove replica node")
            }
        } 
    }

    /**
     *
     */
    fun removeNode(key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("remove_node",
                        arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray())))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("node had been removed successfully")
            } else {
                throw CliError.Companion.CliException("Cannot remove node")
            }
        } 
    }

    /**
     *
     */
    override fun addBlockchainSigners(blockchainRID: String, signers: String) {
        doInTryBlock {
            val nodeList = signers.split(",").map {
                getPostchainClient().query("get_node",
                        GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
            }
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("add_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Blockchain's signers have been added")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain's signers")
            }
        } 
    }

    /**
     *
     */
    fun removeBlockchainSigners(blockchainRID: String, signers: String) {
        doInTryBlock {
            val nodeList = signers.split(",").map {
                getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
            }
            val blockchain = getPostchainClient().query("get_blockchain", GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("remove_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
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
    override fun registerProvider(key: String) {
        doInTryBlock {
            val tx = makeTransactionWithNop().apply {
                addOperation("register_provider",
                        arrayOf(GtvFactory.gtv(key.hexStringToByteArray())))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been added")
            } else {
                throw CliError.Companion.CliException("Cannot add provider")
            }
        } 
    }

    /**
     *
     */
    fun updateProvider(key: String, name: String, beneficiary: String) {
        doInTryBlock {
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
            val tx = makeTransactionWithNop().apply {
                addOperation("update_provider_data", data)
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been updated")
            } else {
                throw CliError.Companion.CliException("Cannot update provider")
            }
        } 
    }


    /**
     *
     */
    override fun enableProvider(key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("enable_provider", arrayOf(provider))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been enable")
            } else {
                throw CliError.Companion.CliException("Cannot enable provider")
            }
        }
        /*
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("enable_provider", arrayOf(provider))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been enable")
            } else {
                throw CliError.Companion.CliException("Cannot enable provider")
            }
        } */
    }

    /**
     *
     */
    fun disableProvider(key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("disable_provider", arrayOf(provider))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been disable")
            } else {
                throw CliError.Companion.CliException("Cannot disable provider")
            }
        } 
    }

}