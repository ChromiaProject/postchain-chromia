package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SigMaker
import net.postchain.client.core.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.config.app.ClientConfig
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.time.Instant

open class CliExecution(val config: ClientConfig) {

    companion object : KLogging()

    protected val cryptoSystem = SECP256K1CryptoSystem()
    protected fun getPostchainClient(): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty()) {
            throw UserMistake("Missing required parameters: brid | pub-key | priv-key")
        }
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(),
                config.privKey.hexStringToByteArray())
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


    /** Add new node. Optionally, also add it to a cluster */
    fun addNodeInternal(key: String, host: String, port: Long, clusterName: String) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        var data: Array<Gtv> = arrayOf(provider)
        data = data.plus(GtvFactory.gtv(key.hexStringToByteArray()))
        data = data.plus(GtvFactory.gtv(host))
        data = data.plus(GtvFactory.gtv(port))
        if (clusterName != "") {
            val cluster = clusterGtv(clusterName)
            data = data.plus(cluster)
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().apply {
            addOperation("add_node", data)
            sign(buildSigMaker())
        }
    }

    /** Add existing node to existing cluster
     * */
    fun addNodeToClusterInternal(key: String, clusterName: String) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_node_to_cluster",
                    arrayOf(provider,
                            GtvFactory.gtv(key.hexStringToByteArray()), node, cluster))
            sign(buildSigMaker())
        }
    }

    /** Create a new container in an existing cluster (TODO: with given resource limits (table container_resource_limit)).
     * Who can create a container and update resource limits? Cluster's deployer.
     * */
    fun createContainerInternal(clusterName: String, containerName: String) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        return makeTransactionWithNop().apply {
            addOperation("create_container",
                    arrayOf(provider,
                            cluster, GtvFactory.gtv(containerName)))
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

    fun getProviderInfo(key: String) : Gtv {
        var returnVal : Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query("get_provider_data",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            returnVal = info
        }
        return returnVal!!
    }

    fun getNodeInfo(key: String) : Gtv {
        var returnVal : Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query("get_node_data",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            returnVal = info
        }
        return returnVal!!
    }

    fun getBlockchainConfiguration(blockchainRID: String, height: Long) : ByteArray {
        var returnVal : ByteArray? = null
        doInTryBlock {
            // it means current height
            var heightConfiguration = height
            if (height == -1L) {
                heightConfiguration = getBlockchainLastHeight(blockchainRID)
            }
            val conf = getPostchainClient().query("nm_get_blockchain_configuration",
                    GtvFactory.gtv(
                            "blockchain_rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()),
                            "height" to GtvFactory.gtv(heightConfiguration))).get().asByteArray()
            returnVal = conf
        }
        return returnVal!!
    }

    fun getBlockchainLastHeight(blockchainRID: String) : Long {
        var returnVal = -1L
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val height = getPostchainClient().query("get_blockchain_last_height",
                    GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger()))).get().asInteger()
            returnVal = height
        }
        return returnVal
    }

    fun getNodeListVersion() : Long {
        var returnVal = 0L
        doInTryBlock {
            val version = getPostchainClient().query("nm_get_peer_list_version",
                    GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).get().asInteger()
            returnVal = version
        }
        return returnVal
    }

    fun listNodes() : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query("nm_get_peer_infos",
                    GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos")))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesWithProvider() : List<Gtv> {
        val nodeList = arrayListOf<Gtv>()
        doInTryBlock {
            val nList = getPostchainClient().query("get_nodes_with_provider",
                    GtvFactory.gtv("type" to GtvFactory.gtv("get_nodes_with_provider")))
                    .get()
                    .asArray()
            nodeList.addAll(nList.map { it })
        }
        return nodeList
    }

    fun listProviders() : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query("get_all_providers",
                    GtvFactory.gtv("type" to GtvFactory.gtv("get_all_providers")))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String) : List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val isNode = getPostchainClient().query("is_node",
                    GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get().asBoolean()
            if (isNode) {
                val list = getPostchainClient().query("nm_compute_blockchain_list", GtvFactory.gtv(
                        "node_id" to GtvFactory.gtv(key.hexStringToByteArray()))).get().asArray()
                listBlockChain.addAll(list.map { it.asByteArray() })
            }
        }
        return listBlockChain
    }

    fun listAllBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query("get_all_blockchains",
                    GtvFactory.gtv("type" to GtvFactory.gtv("get_all_blockchains")))
                    .get()
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listActiveBlockchains(): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query("get_active_blockchains",
                    GtvFactory.gtv("type" to GtvFactory.gtv("get_active_blockchains")))
                    .get()
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun listBlockchainSigners(blockchainRID: String) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list =  getPostchainClient().query("get_blockchain_signers",
                    GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listBlockchainReplicas(blockchainRID: String) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().query("get_blockchain_replicas",
                    GtvFactory.gtv("blockchain" to GtvFactory.gtv(blockchain.asInteger())))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesByProvider(key: String) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val provider = providerGtv(key)
            val list = getPostchainClient().query("get_nodes_by_provider",
                    GtvFactory.gtv("provider" to GtvFactory.gtv(provider.asInteger())))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
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
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("enable_provider", arrayOf(provider))
            sign(buildSigMaker())
        }
    }

    fun disableProviderInternal(key: String): GTXTransactionBuilder {
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("disable_provider", arrayOf(provider))
            sign(buildSigMaker())
        }
    }

    fun createClusterInternal(newClusterName: String, providerKeys: String, govenorSet: String, deployerSet: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        var initials: Gtv
        if (providerKeys.isEmpty()) {
            initials = GtvNull
        } else {
            initials = providersGtv(providerKeys)
        }
        val govenor = voterSetGtv(govenorSet)
        val deployer = voterSetGtv(deployerSet)
        return makeTransactionWithNop().apply {
            addOperation("create_cluster", arrayOf(provider, GtvString(newClusterName), initials, govenor, deployer))
            sign(buildSigMaker())
        }
    }


    fun addBlockchainSignersInternal(blockchainRID: String, signers: String): GTXTransactionBuilder {
        val nodeList = signers.split(",").map { nodeGtv(it) }
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("add_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }

    fun addReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun removeReplicaInternal(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("remove_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun removeNodeInternal(key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation("remove_node",
                    arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray())))
            sign(buildSigMaker())
        }
    }

    fun addNode(key: String, host: String, port: Long, clusterName: String) {
        sendTxSync(addNodeInternal(key, host, port, clusterName), "Node has been enabled", "Cannot add node")
    }


    fun addReplica(blockchainRID: String, key: String) {
        sendTxSync(addReplicaInternal(blockchainRID, key), "Replica added", "Cannot add replica node")
    }

    fun removeReplica(blockchainRID: String, key: String) {
        sendTxSync(removeReplicaInternal(blockchainRID, key), "Replica removed",
                "Cannot remove replica node")
    }

    fun removeNode(key: String) {
        sendTxSync(removeNodeInternal(key), "Node removed", "Cannot remove node")
    }

    private fun voterSetGtv(name: String): Gtv {
        return getPostchainClient().query("get_voter_set", GtvFactory.gtv(
                "name" to GtvFactory.gtv(name))).get()
    }

    fun clusterGtv(name: String): Gtv {
        return getPostchainClient().query("get_cluster", GtvFactory.gtv(
                "name" to GtvFactory.gtv(name))).get()
    }

    fun containerGtv(name: String): Gtv {
        return getPostchainClient().query("get_container", GtvFactory.gtv(
                "name" to GtvFactory.gtv(name))).get()
    }


    //comma separeted list of providers
    fun providersGtv(keys: String): Gtv {
        val gtvList = keys.split(",").map { getPostchainClient().query("get_provider",
                GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
        return GtvFactory.gtv(gtvList)
    }

    //Single provider
    fun providerGtv(key: String): Gtv {
        return getPostchainClient().query("get_provider", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
    }

    fun nodeGtv(key: String): Gtv {
        return getPostchainClient().query("get_node",
                GtvFactory.gtv("pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
    }

    fun blockchainGtv(blockchainRID: String): Gtv {
        return getPostchainClient().query("get_blockchain",
                GtvFactory.gtv("rid" to GtvFactory.gtv(blockchainRID.hexStringToByteArray()))).get()
    }

    fun createVoterSet(name: String, providers: String, threshold: Long, governorName: String) {
        createVoterSetInternal(name, providers, threshold, governorName)
    }

    fun createVoterSetInternal(name: String, providerKeys: String, threshold: Long, governorName: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        var providerList: Gtv
        if (providerKeys.isEmpty()) {
            providerList = GtvNull
        } else {
            providerList = providersGtv(providerKeys)
        }
        var governor: Gtv
        if (governorName.isEmpty()) {
            governor = GtvNull
        } else {
            governor = voterSetGtv(governorName)
        }
        return makeTransactionWithNop().apply {
            addOperation("create_voter_set", arrayOf(meProvider, GtvString(name), GtvInteger(threshold), providerList, governor))
            sign(buildSigMaker())
        }
    }

    fun addCluster(name: String, providers: String, governorName: String, deployersName: String) {
        createClusterInternal(name, providers, governorName, deployersName)
    }
}
