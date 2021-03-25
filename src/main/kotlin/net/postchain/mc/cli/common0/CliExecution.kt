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

//    fun listNodes() : List<Gtv> {
//        val returnList = arrayListOf<Gtv>()
//        doInTryBlock {
//            val list = getPostchainClient().query("nm_get_peer_infos",
//                    GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos")))
//                    .get()
//                    .asArray()
//            returnList.addAll(list.map { it })
//        }
//        return returnList
//    }

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

    fun listBlockchains(includeInactive: Boolean): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query("get_blockchains",
                    GtvFactory.gtv("include_inactive" to GtvFactory.gtv(includeInactive)))
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
                    GtvFactory.gtv("bc" to GtvFactory.gtv(blockchain.asInteger())))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listVoterSetMembers(name: String) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list =  getPostchainClient().query("get_voter_set_members",
                    GtvFactory.gtv("name" to GtvString(name)))
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

    fun listContainerReplicas(containerName: String) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val container = containerGtv(containerName)
            val list = getPostchainClient().query("get_container_replicas",
                    GtvFactory.gtv("container" to GtvFactory.gtv(container.asInteger())))
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

    fun listProposalsSince(rowid: Long) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list =  getPostchainClient().query("get_proposals_since", GtvFactory.gtv(
                    "since" to GtvFactory.gtv(rowid)))
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun getProposal(rowid: Long) : Gtv {
        var returnValue : Gtv? = null
        doInTryBlock {
            val prop =  getPostchainClient().query("get_proposal", GtvFactory.gtv(
                    "rowid" to GtvFactory.gtv(rowid)))
                    .get()
            returnValue = prop
        }
        return returnValue!!
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

    fun registerProvider(key: String) {
        sendTxSync(registerProviderAsync(key, 0), "Provider has been registered",
                "Cannot register provider")
    }

    fun proposeEnableProvider(key: String) {
        sendTxSync(proposeEnableProviderAsync(key), "Enabling of provider has been proposed",
                "Cannot propose enabling of provider")
    }

    fun proposeDisableProvider(key: String) {
        sendTxSync(proposeDisableProviderAsync(key), "Disabling of provider has been proposed",
                "Cannot propose disabling of provider")
    }

    fun createVoterSet(name: String, providers: String, threshold: Long, governorName: String) {
        sendTxSync(createVoterSetAsync(name, providers, threshold, governorName), "voter set created",
                "Cannot create voter set")
    }

    fun addCluster(name: String, providers: String, governorName: String, deployersName: String) {
        sendTxSync(createClusterAsync(name, providers, governorName, deployersName), "Cluster added", "Adding cluster failed")
    }

    fun proposeContainer(containerName: String, clusterName: String, deployerName: String) {
        sendTxSync(proposeContainerAsync(containerName, clusterName, deployerName), "Container proposed", "Failed proposing new container")
    }
    fun addNode(key: String, host: String, port: Long, clusterName: String) {
        sendTxSync(addNodeAsync(key, host, port, clusterName), "Node has been enabled", "Cannot add node")
    }

    fun addBlockchainReplica(blockchainRID: String, key: String) {
        sendTxSync(addBlockchainReplicaAsync(blockchainRID, key), "Replica added", "Cannot add replica")
    }

    fun addContainerReplica(clusterName: String, containerName: String) {
        sendTxSync(addBlockchainReplicaAsync(clusterName, containerName), "Replica added", "Cannot add replica")
    }

    fun removeBlockchainReplica(blockchainRID: String, key: String) {
        sendTxSync(removeBlockchainReplicaAsync(blockchainRID, key), "Replica removed",
                "Cannot remove replica node")
    }

    fun removeContainerReplica(clusterName: String, containerName: String) {
        sendTxSync(removeContainerReplicaAsync(clusterName, containerName), "Replica removed",
                "Cannot remove replica")
    }

    fun removeNode(key: String) {
        sendTxSync(removeNodeAsync(key), "Node removed", "Cannot remove node")
    }

    fun proposeConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)  {
        sendTxSync(proposeConfigurationAsync(blockchainRID, blockchainConfigFile, height, format),
                "proposal of config added", "Cannot add config proposal")

    }

    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(voteAsync(rowid, yes), "vote added successfully", "Cannot add vote")
    }

    fun proposeBlockchain(blockchainConfigFile: String, format: String?, container: String) {
        sendTxSync(proposeBlockchainAsync(blockchainConfigFile, format, container), "Blockchain has been proposed",
                "Cannot add bc proposal")
    }

    fun proposePauseBlockchain(blockchainRID: String) {
        sendTxSync(proposePauseBlockchainAsync(blockchainRID),
                "blockchain pause proposition was added successfully",
                "Cannot add proposal for pausing blockchain")
    }

    fun proposeUnPauseBlockchain(blockchainRID: String) {
        sendTxSync(proposeUnPauseBlockchainAsync(blockchainRID),
                "blockchain un-pause proposition was added successfully",
                "Cannot add proposal for un-pausing blockchain")
    }

    fun proposeDeleteBlockchain(blockchainRID: String) {
        sendTxSync(proposeDeleteBlockchainAsync(blockchainRID),
                "blockchain delete proposition was added successfully",
                "Cannot add proposal for deleting blockchain")
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

    /**
     * Below: Asynchronous versions of operation commands. These are the ones tested in DirectoryTest.kt. They are given
     * a synchronizing skin so that they can be called by the client. Example: CommandAddNode calls addNode() that calls
     * AddNodeAsync().
     */

    fun registerProviderAsync(key: String, tier: Long): GTXTransactionBuilder {
        val me = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation("register_provider",
                    arrayOf(me, GtvFactory.gtv(key.hexStringToByteArray()), GtvInteger(tier)))
            sign(buildSigMaker())
        }
    }

    fun createVoterSetAsync(name: String, providerKeys: String, threshold: Long, governorName: String): GTXTransactionBuilder {
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

    /** Add new node. Optionally, also add it to a cluster */
    fun addNodeAsync(key: String, host: String, port: Long, clusterName: String) : GTXTransactionBuilder {
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

    /** Add existing provider to existing cluster
     * */
    fun addProviderToClusterAsync(key: String, clusterName: String) : GTXTransactionBuilder {
        val me = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_provider_to_cluster",
                    arrayOf(me, provider, cluster))
            sign(buildSigMaker())
        }
    }

    /** Add existing node to existing cluster
     * */
    fun addNodeToClusterAsync(key: String, clusterName: String) : GTXTransactionBuilder {
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

    /** Propose a new (isolated) container with default resource limits and a deployer/configurator voter set in an existing cluster.
     * Who can create a container and update resource limits? Cluster's deployer voter set.
     * */
    fun proposeContainerAsync(containerName: String, clusterName: String, deployerName: String) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val configurator = voterSetGtv(deployerName)
        return makeTransactionWithNop().apply {
            addOperation("propose_container",
                    arrayOf(provider,
                            cluster, GtvFactory.gtv(containerName), configurator))
            sign(buildSigMaker())
        }
    }

    fun createClusterAsync(newClusterName: String, providerKeys: String, govenorSet: String, deployerSet: String): GTXTransactionBuilder {
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

    fun addBlockchainReplicaAsync(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_bc_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun addContainerReplicaAsync(clusterName: String, containerName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation("add_container_replica", arrayOf(provider, cluster, container))
            sign(buildSigMaker())
        }
    }

    fun removeBlockchainReplicaAsync(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("remove_bc_replica", arrayOf(provider, blockchain, node))
            sign(buildSigMaker())
        }
    }

    fun removeContainerReplicaAsync(clusterName: String, containerName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation("remove_bc_replica", arrayOf(provider, cluster, container))
            sign(buildSigMaker())
        }
    }

    fun removeNodeAsync(key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation("remove_node",
                    arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray())))
            sign(buildSigMaker())
        }
    }

    fun proposeProviderIsSystemAsync(pubKey: String, isSystem: Boolean): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val otherProvider = providerGtv(pubKey)
        return makeTransactionWithNop().apply {
            addOperation("propose_provider_is_system", arrayOf(meProvider, otherProvider, GtvFactory.gtv(isSystem)))
            sign(buildSigMaker())
        }
    }

    fun proposeEnableProviderAsync(key: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val providerToBeEnabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_enable_provider", arrayOf(meProvider, providerToBeEnabled))
            sign(buildSigMaker())
        }
    }

    fun proposeDisableProviderAsync(key: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val providerToBeDisabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_disable_provider", arrayOf(meProvider, providerToBeDisabled))
            sign(buildSigMaker())
        }
    }

    fun proposeConfigurationAsync(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)
            : GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_configuration",
                    arrayOf(blockchain, provider, GtvFactory.gtv(data), GtvFactory.gtv(height)))
            sign(buildSigMaker())
        }
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteAsync(rowid: Long, yes: Boolean) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation("make_vote",
                    arrayOf(provider, GtvFactory.gtv(rowid), GtvFactory.gtv((yes))))
            sign(buildSigMaker())
        }
    }

    /**
     * Propose add Blockchain to an existing container
     */
    fun proposeBlockchainAsync(blockchainConfigFile: String, format: String?, container: String) : GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return proposeBc(data, container)
    }

    fun proposeBlockchainGtvAsync(blockchainConfig: Gtv, container: String): GTXTransactionBuilder {
        val data = GtvEncoder.encodeGtv(blockchainConfig)
        return proposeBc(data, container)
    }

    private fun proposeBc(data: ByteArray, containerName: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation("propose_blockchain",
                    arrayOf(meProvider, GtvFactory.gtv(data), container))
            sign(buildSigMaker())
        }
    }

    /** Who can pause a blockchain? Container configurator voter set
     * */
    fun proposePauseBlockchainAsync(blockchainRID: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_pause_blockchain",
                    arrayOf(meProvider, blockchain))
            sign(buildSigMaker())
        }
    }

    /** Who can pause a blockchain? Container configurator voter set
     * */
    fun proposeUnPauseBlockchainAsync(blockchainRID: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_unpause_blockchain",
                    arrayOf(meProvider, blockchain))
            sign(buildSigMaker())
        }
    }

    /** Who can delete a blockchain? Container configurator voter set
     * */
    fun proposeDeleteBlockchainAsync(blockchainRID: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_delete_blockchain",
                    arrayOf(meProvider, blockchain))
            sign(buildSigMaker())
        }
    }

}
