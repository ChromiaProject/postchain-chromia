package net.postchain.mc.cli.chromia0

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SigMaker
import net.postchain.client.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.AppConfig
import java.io.File
import java.time.Instant
import java.util.ArrayList

class CliExecution(val config: AppConfig) {

    companion object : KLogging()

    private val cryptoSystem = SECP256K1CryptoSystem()
    private val postchainClientFactory = PostchainClientFactory()

    private fun getPostchainClient(): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty() || config.privKey.isEmpty()) {
            throw UserMistake("missing required parameters")
        }
        val resolver = postchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
        return postchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(config.brid), DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray()))
    }

    private fun getEncodedGtxValueFromFile(blockchainConfigFile: String) :ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    private fun buildSigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }

    private fun makeTransactionWithNop(): GTXTransactionBuilder {
        return getPostchainClient().makeTransaction().apply {
            addOperation("nop", arrayOf(GtvFactory.gtv(Instant.now().toEpochMilli())))
        }
    }

    /**
     *
     */
    fun addBlockchain(blockchainConfigFile: String, nodes: String) {
        try {
            val data = getEncodedGtxValueFromFile(blockchainConfigFile)
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
    fun stopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        try {
            val tx = makeTransactionWithNop().apply {
                addOperation("stop_blockchain",
                        arrayOf(GtvFactory.gtv(blockchainRID.hexStringToByteArray()), GtvFactory.gtv(removeReplicas)))
                sign(buildSigMaker())
            }
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain was stop successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot stop blockchain")
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
    fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long) {
        try {
            val data = getEncodedGtxValueFromFile(blockchainConfigFile)
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
    fun addNode(key: String, host: String, port: Long) {
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
    fun addReplica(blockchainRID: String, key: String) {
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
    fun addBlockchainSigners(blockchainRID: String, signers: String) {
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
    fun registerProvider(key: String) {
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

    /**
     *
     */
    fun enableProvider(key: String) {
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
        }
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
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String) : List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        try {
            val list = getPostchainClient().query("nm_compute_blockchain_list", GtvFactory.gtv(
                    "node_id" to GtvFactory.gtv(key.hexStringToByteArray()))).get().asArray()
            listBlockChain.addAll(list.map { it -> it.asByteArray() })
            return listBlockChain
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun getProviderInfo(key: String) : Gtv {
        try {
            return getPostchainClient().query("get_provider_data",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun getNodeInfo(key: String) : Gtv {
        try {
            return getPostchainClient().query("get_node_data",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun getBlockchainConfiguration(blockchainRID: String, height: Long) : ByteArray {
        try {
            return getPostchainClient().query("nm_get_blockchain_configuration",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(height))).get().asByteArray()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun getNodeListVersion() : Long {
        try {
            return getPostchainClient().query("nm_get_peer_list_version", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).get().asInteger()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun listNodes() : List<Gtv> {
        try {
            return getPostchainClient().query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos")))
                    .get()
                    .asArray()
                    .map { it }
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun listAllBlockchains(): List<ByteArray> {
        try {
            return getPostchainClient().query("get_all_blockchains", GtvFactory.gtv("type" to GtvFactory.gtv("get_all_blockchains")))
                    .get()
                    .asArray()
                    .map { it.asByteArray() }
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    fun listActiveBlockchains(): List<ByteArray> {
        try {
            return getPostchainClient().query("get_active_blockchains", GtvFactory.gtv("type" to GtvFactory.gtv("get_active_blockchains")))
                    .get()
                    .asArray()
                    .map { it.asByteArray() }
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

}