package net.postchain.mc.cli.common0

import mu.KLogging
import mu.KotlinLogging.logger
import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SigMaker
import net.postchain.client.core.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.time.Instant

abstract class CliExecution(val config: ClientConfig) {

    companion object : KLogging()

    protected val cryptoSystem = SECP256K1CryptoSystem()
    protected fun getPostchainClient(): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty()) {
            throw UserMistake("Missing required parameters: brid | pub-key | priv-key")
        }
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
        val defaultSigner = DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(config.brid), defaultSigner)
    }

    protected fun getEncodedGtxValueFromFile(blockchainConfigFile: String) : ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    protected fun buildSigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }

    protected fun makeTransactionWithNop(): GTXTransactionBuilder {
        return getPostchainClient().makeTransaction().apply {
            addOperation("nop", arrayOf(GtvFactory.gtv(Instant.now().toEpochMilli())))
        }
    }

    /**
     *
     */
    fun addNodeInternal(key: String, host: String, port: Long) : GTXTransactionBuilder {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            return makeTransactionWithNop().apply {
                addOperation("add_node",
                        arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray()), GtvFactory.gtv(host), GtvFactory.gtv(port)))
                sign(buildSigMaker())
        }
    }
    //abstract fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)
//    abstract fun addBlockchain(blockchainConfigFile: String, nodes: String, format: String?)
    //abstract fun addReplica(blockchainRID: String, key: String)
    //abstract fun addBlockchainSigners(blockchainRID: String, key: String)

    fun doInTryBlock(todo: () -> Unit) {
        try {
            todo()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     * format: Format of blockchain configuration file
     */
    fun addBlockchainGtv(blockchainConfig: Gtv, nodes: String) {
        sendTxSync(addBlockchainGtvInternal(blockchainConfig, nodes),"Blockchain added from gtv", "Cannot add blockchain")
//
//        doInTryBlock {
//            val nodeList = nodes.split(",").map { getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
////            val data = blockchainConfig.asDict().toByteArray()
//            val data = GtvEncoder.encodeGtv(blockchainConfig)
////            println(GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(bc)))
//            val tx = makeTransactionWithNop().apply {
//                addOperation("add_blockchain",
//                        arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
//                sign(buildSigMaker())
//            }
//
//            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
//            if (txResult.status == TransactionStatus.CONFIRMED) {
//                println("blockchain from gtv was added successfully!")
//            } else {
//                throw CliError.Companion.CliException("Cannot add blockchain from gtv")
//            }
//        }
    }

    /**
     * format: Format of blockchain configuration file
     */
    fun addBlockchainGtvInternal(blockchainConfig: Gtv, nodes: String): GTXTransactionBuilder {
            val nodeList = nodes.split(",").map { getPostchainClient().query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
            val data = GtvEncoder.encodeGtv(blockchainConfig)
            return makeTransactionWithNop().apply {
                addOperation("add_blockchain",
                        arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
                sign(buildSigMaker())
        }
    }
    /**
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String) : List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query("nm_compute_blockchain_list", GtvFactory.gtv(
                    "node_id" to GtvFactory.gtv(key.hexStringToByteArray()))).get().asArray()
            listBlockChain.addAll(list.map { it -> it.asByteArray() })
        }
        return listBlockChain
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
            // it means current height
            var heightConfiguration = height
            if (height == -1L) {
                val blockchain = getPostchainClient().query("get_blockchain",
                        GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
                heightConfiguration = getPostchainClient().query("get_blockchain_last_height",
                        GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger()))).get().asInteger()
            }
            return getPostchainClient().query("nm_get_blockchain_configuration",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(heightConfiguration))).get().asByteArray()
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

    fun listNodesWithProvider() : List<Gtv> {
        try {
            return getPostchainClient().query("get_nodes_with_provider", GtvFactory.gtv("type" to GtvFactory.gtv("get_nodes_with_provider")))
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

    fun listProviders() : List<Gtv> {
        try {
            return getPostchainClient().query("get_all_providers", GtvFactory.gtv("type" to GtvFactory.gtv("get_all_providers")))
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
            logger.error(e.message, e)
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

    fun listBlockchainSigners(blockchainRID: String) : List<Gtv> {
        try {
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            return getPostchainClient().query("get_blockchain_signers", GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
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

    fun listBlockchainReplicas(blockchainRID: String) : List<Gtv> {
        try {
            val blockchain = getPostchainClient().query("get_blockchain",
                    GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
            return getPostchainClient().query("get_blockchain_replicas", GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
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

    fun listNodesByProvider(key: String) : List<Gtv> {
        try {
            val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            return getPostchainClient().query("get_nodes_by_provider",
                    GtvFactory.gtv("provider" to GtvFactory.gtv(provider.asInteger())))
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

    protected fun readConfigurationFile(blockchainConfigFile: String, format : String?) : ByteArray {
        val configFile = File(blockchainConfigFile)
        var fmt = format
        if (fmt == null) {
            fmt = if (configFile.extension == "gtv") "gtv" else "xml"
        }
        var data : ByteArray
        if (fmt == "gtv") {
            data = configFile.readBytes()
            // try to decode to ensure data is valid
            GtvFactory.decodeGtv(data)
        } else {
            data = getEncodedGtxValueFromFile(blockchainConfigFile)
        }
        return data
    }

    fun sendTxUnconfirmed(tx: GTXTransactionBuilder): TransactionResult {
        return tx.postSync(ConfirmationLevel.NO_WAIT)
    }

    fun sendTx(tx: GTXTransactionBuilder): Promise<TransactionResult, Exception> {
        // Why task? See POS 136
        return task {
            tx.postSync(ConfirmationLevel.UNVERIFIED)
        }
    }

    fun sendTxSync(tx: GTXTransactionBuilder, onSuccess: String, onFail: String) {
        doInTryBlock {
            val txResult = sendTx(tx).get()
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println(onSuccess)
            } else {
                throw CliError.Companion.CliException(onFail)
            }
        }
    }

    fun registerProviderInternal(key: String): GTXTransactionBuilder {
        return makeTransactionWithNop().apply {
            addOperation("register_provider",
                    arrayOf(GtvFactory.gtv(key.hexStringToByteArray())))
            sign(buildSigMaker())
        }
    }



    fun enableProviderInternal(key: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("enable_provider", arrayOf(provider))
            sign(buildSigMaker())
        }
    }

    fun disableProviderInternal(key: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("disable_provider", arrayOf(provider))
            sign(buildSigMaker())
        }
    }

    fun addBlockchainSignersInternal(blockchainRID: String, signers: String): GTXTransactionBuilder {
        val nodeList = signers.split(",").map {
            getPostchainClient().query("get_node",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
        }
        val blockchain = getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("add_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }

    fun addReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
        val blockchain = getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        val node = getPostchainClient().query("get_node",
                GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("add_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun removeReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
        val blockchain = getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
        val node = getPostchainClient().query("get_node",
                GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("remove_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun removeNodeInternal(key: String): GTXTransactionBuilder {
        val provider = getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
        return makeTransactionWithNop().apply {
            addOperation("remove_node",
                    arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray())))
            sign(buildSigMaker())
        }
    }
}
