package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.crypto.SigMaker
import net.postchain.client.core.*
import net.postchain.common.hexStringToByteArray
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.time.Instant

open class CliExecution(val config: ClientConfig) {

    companion object : KLogging()

    protected val cryptoSystem = Secp256K1CryptoSystem()

    protected fun getPostchainClient(): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty()) {
            throw UserMistake("Missing required parameters: brid | pub-key | priv-key")
        }
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(
                config.pubKey.hexStringToByteArray(),
                config.privKey.hexStringToByteArray()
        )
        val defaultSigner = DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(config.brid), defaultSigner)
    }

    protected fun getEncodedGtxValueFromFile(blockchainConfigFile: File): ByteArray {
        val gtv = GtvMLParser.parseGtvML(blockchainConfigFile.readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    protected fun buildSigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }

    protected fun makeTransactionWithNop(): GTXTransactionBuilder {
        return getPostchainClient().makeTransaction().apply {
            addOperation("nop", gtv(Instant.now().toEpochMilli()))
        }
    }


    fun addNodeInternal(key: String, host: String, port: Long): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation(
                    "add_node",
                    provider,
                    gtv(key.hexStringToByteArray()), gtv(host), gtv(port)
            )
            sign(buildSigMaker())
        }
    }

    fun doInTryBlock(todo: () -> Unit) {
        try {
            todo()
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException(e.message!!)
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException(e.message!!)
        }
    }

    fun getProviderInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_provider_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get()
            returnVal = info
        }
        return returnVal!!
    }

    fun getNodeInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_node_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get()
            returnVal = info
        }
        return returnVal!!
    }

    fun getBlockchainConfiguration(blockchainRID: String, height: Long): ByteArray {
        var returnVal: ByteArray? = null
        doInTryBlock {
            // it means current height
            var heightConfiguration = height
            if (height == -1L) {
                heightConfiguration = getBlockchainLastHeight(blockchainRID)
            }
            val conf = getPostchainClient().query(
                    "nm_get_blockchain_configuration",
                    gtv(
                            "blockchain_rid" to gtv(blockchainRID.hexStringToByteArray()),
                            "height" to gtv(heightConfiguration)
                    )
            ).get().asByteArray()
            returnVal = conf
        }
        return returnVal!!
    }

    fun getBlockchainLastHeight(blockchainRID: String): Long {
        var returnVal = -1L
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val height = getPostchainClient().query(
                    "get_blockchain_last_height",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            ).get().asInteger()
            returnVal = height
        }
        return returnVal
    }

    fun getNodeListVersion(): Long {
        var returnVal = 0L
        doInTryBlock {
            val version = getPostchainClient().query(
                    "nm_get_peer_list_version",
                    gtv("type" to gtv("nm_get_peer_list_version"))
            ).get().asInteger()
            returnVal = version
        }
        return returnVal
    }

    fun listNodes(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "nm_get_peer_infos",
                    gtv("type" to gtv("nm_get_peer_infos"))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesWithProvider(): List<Gtv> {
        val nodeList = arrayListOf<Gtv>()
        doInTryBlock {
            val nList = getPostchainClient().query(
                    "get_nodes_with_provider",
                    gtv("type" to gtv("get_nodes_with_provider"))
            )
                    .get()
                    .asArray()
            nodeList.addAll(nList.map { it })
        }
        return nodeList
    }

    fun listProviders(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_all_providers",
                    gtv("type" to gtv("get_all_providers"))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val isNode = getPostchainClient().query(
                    "is_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get().asBoolean()
            if (isNode) {
                val list = getPostchainClient().query(
                        "nm_compute_blockchain_list", gtv(
                        "node_id" to gtv(key.hexStringToByteArray())
                )
                ).get().asArray()
                listBlockChain.addAll(list.map { it.asByteArray() })
            }
        }
        return listBlockChain
    }

    fun listAllBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_all_blockchains",
                    gtv("type" to gtv("get_all_blockchains"))
            )
                    .get()
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listActiveBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_active_blockchains",
                    gtv("type" to gtv("get_active_blockchains"))
            )
                    .get()
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listBlockchainSigners(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().query(
                    "get_blockchain_signers",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listBlockchainReplicas(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().query(
                    "get_blockchain_replicas",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesByProvider(key: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val provider = providerGtv(key)
            val list = getPostchainClient().query(
                    "get_nodes_by_provider",
                    gtv("provider" to gtv(provider.asInteger()))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    protected fun readConfigurationFile(blockchainConfigFile: File, format: String?): ByteArray {
        var fmt = format
        if (fmt == null) {
            fmt = if (blockchainConfigFile.extension == "gtv") "gtv" else "xml"
        }
        var data: ByteArray
        if (fmt == "gtv") {
            data = blockchainConfigFile.readBytes()
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
            addOperation(
                    "register_provider",
                    gtv(key.hexStringToByteArray())
            )
            sign(buildSigMaker())
        }
    }

    fun enableProviderInternal(key: String): GTXTransactionBuilder {
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("enable_provider", provider)
            sign(buildSigMaker())
        }
    }

    fun disableProviderInternal(key: String): GTXTransactionBuilder {
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("disable_provider", provider)
            sign(buildSigMaker())
        }
    }

    fun addBlockchainSignersInternal(blockchainRID: String, signers: String, heightDelay: Long): GTXTransactionBuilder {
        val nodeList = signers.split(",").map { nodeGtv(it) }
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            if (heightDelay == -1L) {
                addOperation(
                        "add_blockchain_signers",
                        blockchain, gtv(nodeList)
                )
            } else {
                addOperation(
                        "add_blockchain_signers",
                        blockchain, gtv(nodeList), gtv(heightDelay)
                )
            }
            sign(buildSigMaker())
        }
    }

    fun addReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_replica", provider, blockchain, node)
            sign(buildSigMaker())
        }
    }

    fun removeReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("remove_replica", provider, blockchain, node)
            sign(buildSigMaker())
        }
    }

    fun removeNodeInternal(key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation(
                    "remove_node",
                    provider, gtv(key.hexStringToByteArray())
            )
            sign(buildSigMaker())
        }
    }

    fun addNode(key: String, host: String, port: Long) {
        sendTxSync(addNodeInternal(key, host, port), "Node has been enabled", "Cannot add node")
    }


    fun addReplica(blockchainRID: String, key: String) {
        sendTxSync(addReplicaInternal(blockchainRID, key), "Replica added", "Cannot add replica node")
    }

    fun removeReplica(blockchainRID: String, key: String) {
        sendTxSync(
                removeReplicaInternal(blockchainRID, key), "Replica removed",
                "Cannot remove replica node"
        )
    }


    fun removeNode(key: String) {
        sendTxSync(removeNodeInternal(key), "Node removed", "Cannot remove node")
    }


    fun providerGtv(key: String): Gtv {
        val provider = getPostchainClient().query(
                "get_provider", gtv(
                "pubkey" to gtv(key.hexStringToByteArray())
        )
        ).get()
        return provider
    }

    fun nodeGtv(key: String): Gtv {
        val node = getPostchainClient().query(
                "get_node",
                gtv("pubkey" to gtv(key.hexStringToByteArray()))
        ).get()
        return node
    }

    fun blockchainGtv(blockchainRID: String): Gtv {
        val blockchain = getPostchainClient().query(
                "get_blockchain",
                gtv("rid" to gtv(blockchainRID.hexStringToByteArray()))
        ).get()
        return blockchain
    }
}
