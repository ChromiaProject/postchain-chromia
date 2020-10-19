package net.postchain.mc.cli.enterprise0

import mu.KLogging
import net.postchain.client.core.ConfirmationLevel
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import java.io.File

class CliExecution(config: ClientConfig) : CliExecution(config) {

    companion object : KLogging()


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

//    (blockchain, provider, config_data: byte_array, height: integer)
    fun proposeConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?) {
    doInTryBlock {
            val data = readConfigurationFile(blockchainConfigFile, format)
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("propose_configuration",
                        arrayOf(blockchain, provider, GtvFactory.gtv(data), GtvFactory.gtv(height)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("proposed configuration was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add proposed configuration")
            }
        }
    }

    fun proposeProvider(key: String) {
        doInTryBlock {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("propose_provider",
                        arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray())))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("proposed provider was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add proposed provider")
            }
        }
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a block signing node can vote for a pending configuration.
     */
    fun vote(rowid: Long, yes: Boolean) {
        doInTryBlock {
            val proposal = getPostchainClient().query("get_proposal", GtvFactory.gtv(
                    "rowid" to GtvFactory.gtv(rowid))).get()
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("make_vote",
                        arrayOf(provider, proposal, GtvFactory.gtv((yes))))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("vote added successfully.")
            } else {
                throw CliError.Companion.CliException("Cannot add vote")
            }
        }
    }
    /**
     *
     */
    fun proposeEnableProvider(key: String) {
        doInTryBlock {
            val meProvider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val providerToBeEnabled = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("propose_enable_provider", arrayOf(meProvider, providerToBeEnabled))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been enable")
            } else {
                throw CliError.Companion.CliException("Cannot enable provider")
            }
        }
    }

    /**
     *
     */
    fun proposeBlockchain(blockchainConfigFile: String, nodes: String, format: String?) {
        doInTryBlock {
            val meProvider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val data = readConfigurationFile(blockchainConfigFile, format)
            val nodeList = nodes.split(",").map { getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }

            val tx = makeTransactionWithNop().apply {
                addOperation("propose_blockchain", arrayOf(meProvider, GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
                sign(buildSigMaker())
            }
            println("Propose bc tx: ${tx.toString()}")
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Blockchain has been proposed")
            } else {
                throw CliError.Companion.CliException("Cannot add proposition of blockchain")
            }
        }
    }
    /**
     *
     */
    fun proposeDisableProvider(key: String) {
        doInTryBlock {
            val meProvider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val providerToBeDisabled = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("propose_disable_provider", arrayOf(meProvider, providerToBeDisabled))
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
        /**
     *
     */
    fun proposeStopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        try {
            val meProvider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            val tx = makeTransactionWithNop().apply {
                addOperation("propose_stop_blockchain",
                        arrayOf(meProvider, blockchain, GtvFactory.gtv(removeReplicas)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain stop proposition was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add proposal for stopping blockchain")
            }
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    override fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    override fun addNode(key: String, host: String, port: Long) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    override fun addReplica(blockchainRID: String, key: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun removeReplica(blockchainRID: String, key: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun removeNode(key: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    override fun addBlockchainSigners(blockchainRID: String, signers: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun removeBlockchainSigners(blockchainRID: String, signers: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    override fun registerProvider(key: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun updateProvider(key: String, name: String, beneficiary: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

//    fun doInTryBlock(todo: () -> Unit) {
//        try {
//            todo()
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }


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
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }*/
    }

    /**
     *
     */
    fun disableProvider(key: String) {
        try {
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
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     * This operation initializes the database with a first provider. If table providers is empty, the public key from the module argument is
     * registered as a first provider and enabled. The system needs at least one provider, that can vote for update propsals.
     */
    fun init() {
        doInTryBlock {
            val tx = makeTransactionWithNop().apply {
                addOperation("init", arrayOf<Gtv>())
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Initial provider added and enabled")
            } else {
                throw CliError.Companion.CliException("Cannot add and enable initial provider")
            }
        }
    }

    /**
     * key - publicKey of node
     */
//    fun listBlockchainsForNode(key: String) : List<ByteArray> {
//        val listBlockChain = arrayListOf<ByteArray>()
//        try {
//            val list = getPostchainClient().query("nm_compute_blockchain_list", GtvFactory.gtv(
//                    "node_id" to GtvFactory.gtv(key.hexStringToByteArray()))).get().asArray()
//            listBlockChain.addAll(list.map { it -> it.asByteArray() })
//            return listBlockChain
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun getProviderInfo(key: String) : Gtv {
//        try {
//            return getPostchainClient().query("get_provider_data",
//                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun getNodeInfo(key: String) : Gtv {
//        try {
//            return getPostchainClient().query("get_node_data",
//                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun getBlockchainConfiguration(blockchainRID: String, height: Long) : ByteArray {
//        try {
//            // it means current height
//            var heightConfiguration = height
//            if (height == -1L) {
//                val blockchain = getPostchainClient().query("get_blockchain",
//                        GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
//                heightConfiguration = getPostchainClient().query("get_blockchain_last_height",
//                        GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger()))).get().asInteger()
//            }
//            return getPostchainClient().query("nm_get_blockchain_configuration",
//                GtvFactory.gtv(
//                        "blockchain_rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()),
//                        "height" to GtvFactory.gtv(heightConfiguration))).get().asByteArray()
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }

//    fun getNodeListVersion() : Long {
//        try {
//            return getPostchainClient().query("nm_get_peer_list_version", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).get().asInteger()
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }

//    fun listNodes() : List<Gtv> {
//        try {
//            return getPostchainClient().query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos")))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listNodesWithProvider() : List<Gtv> {
//        try {
//            return getPostchainClient().query("get_nodes_with_provider", GtvFactory.gtv("type" to GtvFactory.gtv("get_nodes_with_provider")))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listProviders() : List<Gtv> {
//        try {
//            return getPostchainClient().query("get_all_providers", GtvFactory.gtv("type" to GtvFactory.gtv("get_all_providers")))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listAllBlockchains(): List<ByteArray> {
//        try {
//            return getPostchainClient().query("get_all_blockchains", GtvFactory.gtv("type" to GtvFactory.gtv("get_all_blockchains")))
//                    .get()
//                    .asArray()
//                    .map { it.asByteArray() }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listActiveBlockchains(): List<ByteArray> {
//        try {
//            return getPostchainClient().query("get_active_blockchains", GtvFactory.gtv("type" to GtvFactory.gtv("get_active_blockchains")))
//                    .get()
//                    .asArray()
//                    .map { it.asByteArray() }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listBlockchainSigners(blockchainRID: String) : List<Gtv> {
//        try {
//            val blockchain = getPostchainClient().query("get_blockchain",
//                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
//            return getPostchainClient().query("get_blockchain_signers", GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listBlockchainReplicas(blockchainRID: String) : List<Gtv> {
//        try {
//            val blockchain = getPostchainClient().query("get_blockchain",
//                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
//            return getPostchainClient().query("get_blockchain_replicas", GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    fun listNodesByProvider(key: String) : List<Gtv> {
//        try {
//            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
//                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
//            return getPostchainClient().query("get_nodes_by_provider",
//                    GtvFactory.gtv("provider" to GtvFactory.gtv(provider.asInteger())))
//                    .get()
//                    .asArray()
//                    .map { it }
//        } catch (e: UserMistake) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
//        } catch (e: Exception) {
//            logger.error(e.message)
//            throw CliError.Companion.CliException("System Error: Something wrong happen")
//        }
//    }
//
//    private fun readConfigurationFile(blockchainConfigFile: String, format : String?) : ByteArray {
//        val configFile = File(blockchainConfigFile)
//        var fmt = format
//        if (fmt == null) {
//            fmt = if (configFile.extension == "gtv") "gtv" else "xml"
//        }
//        var data : ByteArray
//        if (fmt == "gtv") {
//            data = configFile.readBytes()
//            // try to decode to ensure data is valid
//            GtvFactory.decodeGtv(data)
//        } else {
//            data = getEncodedGtxValueFromFile(blockchainConfigFile)
//        }
//        return data
//    }
}