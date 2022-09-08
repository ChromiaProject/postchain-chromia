package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.crypto.SigMaker
import net.postchain.client.core.*
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import java.io.File
import java.time.Instant

open class CliExecution(val config: PostchainClientConfig) {

    companion object : KLogging()

    protected val cryptoSystem = Secp256K1CryptoSystem()

    protected fun getPostchainClient(): PostchainClient {
        return ConcretePostchainClientProvider().createClient(config)
    }

    protected fun getEncodedGtxValueFromFile(blockchainConfigFile: File): ByteArray {
        val gtv = GtvMLParser.parseGtvML(blockchainConfigFile.readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    protected fun makeTransactionWithNop(): TransactionBuilder {
        return getPostchainClient().transactionBuilder().addNop()
    }


    fun addNodeInternal(key: String, host: String, port: Long): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                    "add_node",
                    gtv(config.signers.first().pubKey.key),
                    gtv(key.hexStringToByteArray()), gtv(host), gtv(port)
            )
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
            val info = getPostchainClient().querySync(
                    "get_provider_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )
            returnVal = info
        }
        return returnVal!!
    }

    fun getNodeInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().querySync(
                    "get_node_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )
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
            val conf = getPostchainClient().querySync(
                    "nm_get_blockchain_configuration",
                    gtv(
                            "blockchain_rid" to gtv(blockchainRID.hexStringToByteArray()),
                            "height" to gtv(heightConfiguration)
                    )
            ).asByteArray()
            returnVal = conf
        }
        return returnVal!!
    }

    fun getBlockchainLastHeight(blockchainRID: String): Long {
        var returnVal = -1L
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val height = getPostchainClient().querySync(
                    "get_blockchain_last_height",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            ).asInteger()
            returnVal = height
        }
        return returnVal
    }

    fun getNodeListVersion(): Long {
        var returnVal = 0L
        doInTryBlock {
            val version = getPostchainClient().querySync(
                    "nm_get_peer_list_version",
                    gtv("type" to gtv("nm_get_peer_list_version"))
            ).asInteger()
            returnVal = version
        }
        return returnVal
    }

    fun listNodes(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "nm_get_peer_infos",
                    gtv("type" to gtv("nm_get_peer_infos"))
            )
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesWithProvider(): List<Gtv> {
        val nodeList = arrayListOf<Gtv>()
        doInTryBlock {
            val nList = getPostchainClient().querySync(
                    "get_nodes_with_provider",
                    gtv("type" to gtv("get_nodes_with_provider"))
            )
                    .asArray()
            nodeList.addAll(nList.map { it })
        }
        return nodeList
    }

    fun listProviders(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_all_providers",
                    gtv("type" to gtv("get_all_providers"))
            )
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
            val isNode = getPostchainClient().querySync(
                    "is_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).asBoolean()
            if (isNode) {
                val list = getPostchainClient().querySync(
                        "nm_compute_blockchain_list", gtv(
                        "node_id" to gtv(key.hexStringToByteArray())
                )
                ).asArray()
                listBlockChain.addAll(list.map { it.asByteArray() })
            }
        }
        return listBlockChain
    }

    fun listAllBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_all_blockchains",
                    gtv("type" to gtv("get_all_blockchains"))
            )
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listActiveBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_active_blockchains",
                    gtv("type" to gtv("get_active_blockchains"))
            )
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listBlockchainSigners(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().querySync(
                    "get_blockchain_signers",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            )
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listBlockchainReplicas(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().querySync(
                    "get_blockchain_replicas",
                    gtv("blockchain" to gtv(blockchain.asInteger()))
            )
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesByProvider(key: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val provider = providerGtv(key)
            val list = getPostchainClient().querySync(
                    "get_nodes_by_provider",
                    gtv("provider" to gtv(provider.asInteger()))
            )
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

    fun sendTxUnconfirmed(tx: TransactionBuilder): TransactionResult {
        return tx.postSync()
    }

    fun sendTxSync(tx: TransactionBuilder, onSuccess: String, onFail: String) {
        doInTryBlock {
            val txResult = tx.postSyncAwaitConfirmation()
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println(onSuccess)
            } else {
                throw CliError.Companion.CliException(onFail)
            }
        }
    }

    fun registerProviderInternal(key: String): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                    "register_provider",
                    gtv(key.hexStringToByteArray())
            )
    }

    fun enableProviderInternal(key: String): TransactionBuilder {
        val provider = providerGtv(key)
        return makeTransactionWithNop().addOperation("enable_provider", provider)
    }

    fun disableProviderInternal(key: String): TransactionBuilder {
        val provider = providerGtv(key)
        return makeTransactionWithNop().addOperation("disable_provider", provider)
    }

    fun addBlockchainSignersInternal(blockchainRID: String, signers: String, heightDelay: Long): TransactionBuilder {
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
        }
    }

    fun addReplicaInternal(blockchainRID: String, key: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().addOperation("add_replica", provider, blockchain, node)
    }

    fun removeReplicaInternal(blockchainRID: String, key: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().addOperation("remove_replica", provider, blockchain, node)
    }

    fun removeNodeInternal(key: String): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                    "remove_node",
                    gtv(config.signers.first().pubKey.key), gtv(key.hexStringToByteArray())
            )
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
        val provider = getPostchainClient().querySync(
                "get_provider", gtv(
                "pubkey" to gtv(key.hexStringToByteArray())
        )
        )
        return provider
    }

    fun nodeGtv(key: String): Gtv {
        val node = getPostchainClient().querySync(
                "get_node",
                gtv("pubkey" to gtv(key.hexStringToByteArray()))
        )
        return node
    }

    fun blockchainGtv(blockchainRID: String): Gtv {
        val blockchain = getPostchainClient().querySync(
                "get_blockchain",
                gtv("rid" to gtv(blockchainRID.hexStringToByteArray()))
        )
        return blockchain
    }
}
