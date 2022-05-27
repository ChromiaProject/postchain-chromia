package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.client.core.*
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.*
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

    fun doInTryBlock(todo: () -> Unit) {
        try {
            todo()
        } catch (e: UserMistake) {
            logger.error(e) {}
            throw CliError.Companion.CliException(e.message!!)
        } catch (e: Exception) {
            logger.error(e) {}
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

    fun getClusterInfo(name: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_cluster_data",
                    gtv("name" to gtv(name))
            ).get()
            returnVal = info
        }
        return returnVal!!
    }

    fun listProvidersActionPoints(key: String): Long {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_provider_points",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get()
            returnVal = info
        }
        return returnVal!!.asInteger()
    }

    fun getNodeInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_node_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get().asDict().toMutableMap()
            val cluster_info = getPostchainClient().query(
                    "list_clusters_of_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get()
            info.set("cluster", cluster_info)
            returnVal = gtv(info)
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

    fun listClusterLimits(name: String): Map<String, Long> {
        var listLimits = mapOf<String, Long>()
        doInTryBlock {
            val d = getPostchainClient().query(
                    "nm_get_cluster_limits",
                    gtv("name" to gtv(name))
            )
                    .get()
                    .asDict()
            listLimits = d.mapValues { it.value.asInteger() }
        }
        return listLimits
    }


    fun listContainerLimits(name: String): Map<String, Long> {
        var listLimits = mapOf<String, Long>()
        doInTryBlock {
            val d = getPostchainClient().query(
                    "nm_get_container_limits",
                    gtv("name" to gtv(name))
            )
                    .get()
                    .asDict()
            listLimits = d.mapValues { it.value.asInteger() }
        }
        return listLimits
    }

    /**
     * key - publicKey of node
     */
    fun listContainersForNode(key: String): List<String> {
        val listContainers = arrayListOf<String>()
        doInTryBlock {
            val isNode = getPostchainClient().query(
                    "is_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).get().asBoolean()
            if (isNode) {
                val list = getPostchainClient().query(
                        "nm_get_containers", gtv(
                        "pubkey" to gtv(key.hexStringToByteArray())
                )
                ).get().asArray()
                listContainers.addAll(list.map { it.asString() })
            }
        }
        return listContainers
    }

    fun listClustersForProvider(key: String): List<String> {
        val listClusters = arrayListOf<String>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_provider_clusters", gtv(
                    "pubkey" to gtv(key.hexStringToByteArray())
            )
            ).get().asArray()
            listClusters.addAll(list.map { it.asString() })
        }
        return listClusters
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

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForContainer(name: String): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "nm_get_blockchains_for_container", gtv(
                    "container_name" to GtvString(name)
            )
            ).get().asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun getContainerForBlockchain(blockchainRid: String): String? {
        var container: String? = null
        doInTryBlock {
            container = getPostchainClient().query(
                    "nm_get_container_for_blockchain",
                    gtv("blockchain_rid" to gtv(blockchainRid.hexStringToByteArray()))
            ).get().asString()
        }
        return container
    }

    fun listBlockchainDependencies(blockchainRID: String, height: Long): List<Pair<ByteArray, String>> {
        val listBlockChainContainerPair = arrayListOf<Pair<ByteArray, String>>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().query(
                    "nm_get_blockchain_dependencies",
                    gtv(
                            "blockchain" to gtv(blockchain.asInteger()),
                            "height" to GtvInteger(height)
                    )
            )
                    .get()
                    .asArray()
            list.forEach {
                val pair = it.asArray()
                val rid = pair[0].asByteArray()
                val container = pair[1].asString()
                listBlockChainContainerPair.add(rid to container)
            }
        }
        return listBlockChainContainerPair
    }

    fun listBlockchains(includeInactive: Boolean): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_blockchains",
                    gtv("include_inactive" to gtv(includeInactive))
            )
                    .get()
                    .asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

//    fun listActiveBlockchains(): List<ByteArray> {
//        val listBlockChain = arrayListOf<ByteArray>()
//        doInTryBlock {
//            val list = getPostchainClient().query("get_active_blockchains",
//                    GtvFactory.gtv("type" to GtvFactory.gtv("get_active_blockchains")))
//                    .get()
//                    .asArray()
//            listBlockChain.addAll(list.map { it.asByteArray() })
//        }
//        return listBlockChain
//    }

    fun listBlockchainSigners(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().query(
                    "get_blockchain_signers",
                    gtv("bc" to gtv(blockchain.asInteger()))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listVoterSetMembers(name: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_voter_set_members",
                    gtv("name" to GtvString(name))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listVoterSets(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "list_voter_sets",
                    gtv("type" to gtv("list_voter_sets"))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun getVoterSetGovernor(name: String): String {
        var returnValue = ""
        doInTryBlock {
            val governorName = getPostchainClient().query(
                    "get_voter_set_governor",
                    gtv("name" to GtvString(name))
            )
                    .get()
                    .asString()
            returnValue = governorName
        }
        return returnValue
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


    fun listContainerReplicas(containerName: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val container = containerGtv(containerName)
            val list = getPostchainClient().query(
                    "get_container_replicas",
                    gtv("container" to gtv(container.asInteger()))
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

    fun listClusters(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "list_clusters",
                    gtv("type" to gtv("list_clusters"))
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listProposalsSince(rowid: Long): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().query(
                    "get_proposals_since", gtv(
                    "since" to gtv(rowid)
            )
            )
                    .get()
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun getProposal(rowid: Long): Gtv {
        var returnValue: Gtv? = null
        doInTryBlock {
            val prop = getPostchainClient().query(
                    "get_proposal", gtv(
                    "rowid" to gtv(rowid)
            )
            )
                    .get()
            returnValue = prop
        }
        return returnValue!!
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

    fun registerProvider(key: String, tier: Long) {
        sendTxSync(
                registerProviderAsync(key, tier), "Provider has been registered",
                "Cannot register provider"
        )
    }

    fun updateProvider(key: String, name: String, beneficiary: String) {
        sendTxSync(
                updateProviderAsync(key, name, beneficiary), "Provider data has been updated",
                "Cannot update provider"
        )
    }

    fun updateProviderAsync(key: String, name: String, beneficiary: String): GTXTransactionBuilder {
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
        return makeTransactionWithNop().apply {
            addOperation("update_provider_data", *data)
            sign(buildSigMaker())
        }
    }

    fun createVoterSet(name: String, providers: String, threshold: Long, governorName: String) {
        sendTxSync(
                createVoterSetAsync(name, providers, threshold, governorName), "voter set created",
                "Cannot create voter set"
        )
    }

    fun addCluster(name: String, providers: String, governorName: String, deployersName: String) {
        sendTxSync(
                createClusterAsync(name, providers, governorName, deployersName),
                "Cluster added",
                "Adding cluster failed"
        )
    }

    fun transferActionPoints(to: String, amount: Long) {
        sendTxSync(
                transferActionPointsAsync(to, amount),
                "Action points transferred",
                "Transferring action points failed"
        )
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
        sendTxSync(
                removeBlockchainReplicaAsync(blockchainRID, key), "Replica removed",
                "Cannot remove replica node"
        )
    }

    fun removeContainerReplica(clusterName: String, containerName: String) {
        sendTxSync(
                removeContainerReplicaAsync(clusterName, containerName), "Replica removed",
                "Cannot remove replica"
        )
    }

    fun removeNode(key: String) {
        sendTxSync(removeNodeAsync(key), "Node removed", "Cannot remove node")
    }

    fun proposeConfiguration(
            blockchainRID: String,
            blockchainConfigFile: String,
            height: Long,
            format: String?,
            force: Boolean
    ) {
        sendTxSync(
                proposeConfigurationAsync(blockchainRID, File(blockchainConfigFile), height, format, force),
                "proposal of config added", "Cannot add config proposal"
        )
    }

    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(voteAsync(rowid, yes), "vote added successfully", "Cannot add vote")
    }

    fun proposeEnableProvider(key: String) {
        sendTxSync(
                proposeEnableProviderAsync(key), "Enabling of provider has been proposed",
                "Cannot propose enabling of provider"
        )
    }

    fun proposeDisableProvider(key: String) {
        sendTxSync(
                proposeDisableProviderAsync(key), "Disabling of provider has been proposed",
                "Cannot propose disabling of provider"
        )
    }

    fun proposeClusterLimits(clusterName: String, limitMap: Map<String, Long>) {
        sendTxSync(
                proposeClusterLimitsAsync(clusterName, limitMap),
                "Cluster limits proposed", "Failed proposing new cluster limits"
        )
    }

    fun proposeClusterProvider(clusterName: String, key: String, add: Boolean) {
        sendTxSync(
                proposeClusterProviderAsync(clusterName, key, add),
                "Cluster providers update proposed", "Failed proposing cluster providers update"
        )
    }

    fun proposeClusterDeployer(clusterName: String, key: String) {
        sendTxSync(
                proposeClusterDeployerAsync(clusterName, key),
                "Cluster deployer update proposed", "Failed proposing cluster deployer"
        )
    }

    fun proposeVoterSetGovernor(name: String, new: String) {
        sendTxSync(
                proposeVoterSetGovernorAsync(name, new),
                "Voter set governor update proposed", "Failed proposing voter set governor"
        )
    }

    fun proposeVoterSetMember(voterSet: String, member: String, add: Boolean) {
        sendTxSync(
                proposeVoterSetMemberAsync(voterSet, member, add),
                "Voter set member update proposed", "Failed proposing voter set member update"
        )
    }

    fun proposeContainerLimits(containerName: String, limitMap: Map<String, Long>) {
        sendTxSync(
                proposeContainerLimitsAsync(containerName, limitMap),
                "Container limits proposed", "Failed proposing new container limits"
        )
    }

    fun proposeContainer(containerName: String, clusterName: String, deployerName: String) {
        sendTxSync(
                proposeContainerAsync(containerName, clusterName, deployerName),
                "Container proposed",
                "Failed proposing new container"
        )
    }

    fun proposeBlockchain(blockchainConfigFile: String, format: String?, container: String) {
        sendTxSync(
                proposeBlockchainAsync(File(blockchainConfigFile), format, container), "Blockchain has been proposed",
                "Cannot add bc proposal"
        )
    }

    fun proposePauseBlockchain(blockchainRID: String) {
        sendTxSync(
                proposePauseBlockchainAsync(blockchainRID),
                "blockchain pause proposition was added successfully",
                "Cannot add proposal for pausing blockchain"
        )
    }

    fun proposeUnPauseBlockchain(blockchainRID: String) {
        sendTxSync(
                proposeUnPauseBlockchainAsync(blockchainRID),
                "blockchain un-pause proposition was added successfully",
                "Cannot add proposal for un-pausing blockchain"
        )
    }

    fun proposeDeleteBlockchain(blockchainRID: String) {
        sendTxSync(
                proposeDeleteBlockchainAsync(blockchainRID),
                "blockchain delete proposition was added successfully",
                "Cannot add proposal for deleting blockchain"
        )
    }

    private fun voterSetGtv(name: String): Gtv {
        return getPostchainClient().query(
                "get_voter_set", gtv(
                "name" to gtv(name)
        )
        ).get()
    }

    fun clusterGtv(name: String): Gtv {
        return getPostchainClient().query(
                "get_cluster", gtv(
                "name" to gtv(name)
        )
        ).get()
    }

    fun containerGtv(name: String): Gtv {
        return getPostchainClient().query(
                "get_container", gtv(
                "name" to gtv(name)
        )
        ).get()
    }

    //comma separeted list of providers
    fun providersGtv(keys: String): Gtv {
        val gtvList = keys.split(",").map {
            getPostchainClient().query(
                    "get_provider",
                    gtv("pubkey" to gtv(it.hexStringToByteArray()))
            ).get()
        }
        return gtv(gtvList)
    }

    //Single provider
    fun providerGtv(key: String): Gtv {
        return getPostchainClient().query(
                "get_provider", gtv(
                "pubkey" to gtv(key.hexStringToByteArray())
        )
        ).get()
    }

    fun nodeGtv(key: String): Gtv {
        return getPostchainClient().query(
                "get_node",
                gtv("pubkey" to gtv(key.hexStringToByteArray()))
        ).get()
    }

    fun blockchainGtv(blockchainRID: String): Gtv {
        return getPostchainClient().query(
                "get_blockchain",
                gtv("rid" to gtv(blockchainRID.hexStringToByteArray()))
        ).get()
    }

    /**
     * Below: Asynchronous versions of operation commands. These are the ones tested in DirectoryTest.kt. They are given
     * a synchronizing skin so that they can be called by the client. Example: CommandAddNode calls addNode() that calls
     * AddNodeAsync().
     */

    fun registerProviderAsync(key: String, tier: Long): GTXTransactionBuilder {
        val me = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation(
                    "register_provider",
                    me, gtv(key.hexStringToByteArray()), GtvInteger(tier)
            )
            sign(buildSigMaker())
        }
    }

    fun createVoterSetAsync(
            name: String,
            providerKeys: String,
            threshold: Long,
            governorName: String
    ): GTXTransactionBuilder {
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
            addOperation(
                    "create_voter_set",
                    meProvider, GtvString(name), GtvInteger(threshold), providerList, governor
            )
            sign(buildSigMaker())
        }
    }

    /** Add new node. Optionally, also add it to a cluster */
    fun addNodeAsync(key: String, host: String, port: Long, clusterName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        var data: Array<Gtv> = arrayOf(provider)
        data = data.plus(gtv(key.hexStringToByteArray()))
        data = data.plus(gtv(host))
        data = data.plus(gtv(port))
        if (clusterName != "") {
            val cluster = clusterGtv(clusterName)
            data = data.plus(cluster)
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().apply {
            addOperation("add_node", *data)
            sign(buildSigMaker())
        }
    }

    /** Add existing provider to existing cluster
     * */
    fun addProviderToClusterAsync(key: String, clusterName: String): GTXTransactionBuilder {
        val me = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val provider = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation(
                    "add_provider_to_cluster",
                    me, provider, cluster
            )
            sign(buildSigMaker())
        }
    }

    /** Add existing node to existing cluster
     * */
    fun addNodeToClusterAsync(key: String, clusterName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation(
                    "add_node_to_cluster",
                    provider,
                    gtv(key.hexStringToByteArray()), node, cluster
            )
            sign(buildSigMaker())
        }
    }

    /** Propose a new container resource limits.
     * Who can update container resource limits? Cluster's deployer voter set.
     * */
    fun proposeContainerLimitsAsync(containerName: String, limits: Map<String, Long>): GTXTransactionBuilder {
        val currentLimits = listContainerLimits(containerName).toMutableMap()
        currentLimits.putAll(limits)
        val provider = providerGtv(config.pubKey)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_container_limits",
                    provider,
                    container,
                    gtv(currentLimits["ram"]!!),
                    gtv(currentLimits["cpu"]!!),
                    gtv(currentLimits["storage"]!!)
            )
            sign(buildSigMaker())
        }
    }

    /** Propose a new (isolated) container with default resource limits and a deployer voter set in an existing cluster.
     * Who can create a container and update resource limits? Cluster's deployer voter set.
     * */
    fun proposeContainerAsync(containerName: String, clusterName: String, deployerName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val deployer = voterSetGtv(deployerName)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_container",
                    provider,
                    cluster, gtv(containerName), deployer
            )
            sign(buildSigMaker())
        }
    }

    /** Propose new cluster resource limits.
     * Who can update cluster limits? Cluster governance voter set.
     * */
    fun proposeClusterLimitsAsync(clusterName: String, limits: Map<String, Long>): GTXTransactionBuilder {
        val currentLimits = listClusterLimits(clusterName).toMutableMap()
        currentLimits.putAll(limits)
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_cluster_limits",
                    provider,
                    cluster,
                    gtv(currentLimits["ram"]!!),
                    gtv(currentLimits["cpu"]!!),
                    gtv(currentLimits["storage"]!!)
            )
            sign(buildSigMaker())
        }
    }

    fun transferActionPointsAsync(to: String, amount: Long): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val toProvider = providerGtv(to)
        return makeTransactionWithNop().apply {
            addOperation("transfer_action_points", meProvider, toProvider, gtv(amount))
            sign(buildSigMaker())
        }
    }

    fun createClusterAsync(
            newClusterName: String,
            providerKeys: String,
            govenorSet: String,
            deployerSet: String
    ): GTXTransactionBuilder {
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
            addOperation("create_cluster", provider, GtvString(newClusterName), initials, govenor, deployer)
            sign(buildSigMaker())
        }
    }

    fun addBlockchainReplicaAsync(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("add_bc_replica", provider, blockchain, node)
            sign(buildSigMaker())
        }
    }

    fun addContainerReplicaAsync(clusterName: String, containerName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation("add_container_replica", provider, cluster, container)
            sign(buildSigMaker())
        }
    }

    fun removeBlockchainReplicaAsync(blockchainRID: String, key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("remove_bc_replica", provider, blockchain, node)
            sign(buildSigMaker())
        }
    }

    fun removeContainerReplicaAsync(clusterName: String, containerName: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().apply {
            addOperation("remove_bc_replica", provider, cluster, container)
            sign(buildSigMaker())
        }
    }

    fun removeNodeAsync(key: String): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation(
                    "remove_node",
                    provider, gtv(key.hexStringToByteArray())
            )
            sign(buildSigMaker())
        }
    }

    fun proposeProviderIsSystemAsync(pubKey: String, isSystem: Boolean): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val otherProvider = providerGtv(pubKey)
        return makeTransactionWithNop().apply {
            addOperation("propose_provider_is_system", meProvider, otherProvider, gtv(isSystem))
            sign(buildSigMaker())
        }
    }

    fun proposeEnableProviderAsync(key: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val providerToBeEnabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_enable_provider", meProvider, providerToBeEnabled)
            sign(buildSigMaker())
        }
    }

    fun proposeDisableProviderAsync(key: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val providerToBeDisabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_disable_provider", meProvider, providerToBeDisabled)
            sign(buildSigMaker())
        }
    }

    fun proposeConfigurationAsync(
            blockchainRID: String,
            blockchainConfigFile: File,
            height: Long,
            format: String?,
            force: Boolean
    )
            : GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        val provider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_configuration",
                    blockchain, provider, gtv(data), gtv(height), gtv(force)
            )
            sign(buildSigMaker())
        }
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteAsync(rowid: Long, yes: Boolean): GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation(
                    "make_vote",
                    provider, gtv(rowid), gtv((yes))
            )
            sign(buildSigMaker())
        }
    }

    /**
     * Propose add Blockchain to an existing container
     */
    fun proposeBlockchainAsync(blockchainConfigFile: File, format: String?, container: String): GTXTransactionBuilder {
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
            addOperation(
                    "propose_blockchain",
                    meProvider, gtv(data), container
            )
            sign(buildSigMaker())
        }
    }

    /** Who can pause a blockchain? Container deployer voter set
     * */
    fun proposePauseBlockchainAsync(blockchainRID: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_pause_blockchain",
                    meProvider, blockchain
            )
            sign(buildSigMaker())
        }
    }

    /** Who can pause a blockchain? Container deployer voter set
     * */
    fun proposeUnPauseBlockchainAsync(blockchainRID: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_unpause_blockchain",
                    meProvider, blockchain
            )
            sign(buildSigMaker())
        }
    }

    /** Who can delete a blockchain? Container deployer voter set
     * */
    fun proposeDeleteBlockchainAsync(blockchainRID: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_delete_blockchain",
                    meProvider, blockchain
            )
            sign(buildSigMaker())
        }
    }

    /**
     * Propose update of cluster providers
     * add = true => Add this provider
     * add = false => Remove this provider from cluster
     */
    fun proposeClusterProviderAsync(clusterName: String, provider: String, add: Boolean): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val providerToAddOrRemove = providerGtv(provider)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_cluster_provider",
                    meProvider, cluster, providerToAddOrRemove, gtv(add)
            )
            sign(buildSigMaker())
        }
    }

    fun proposeClusterDeployerAsync(clusterName: String, newDeployer: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        val deployer = voterSetGtv(newDeployer)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_cluster_deployer",
                    meProvider, cluster, deployer
            )
            sign(buildSigMaker())
        }
    }

    /**
     * Propose update a voter set's members
     * add = true => Add this provider as member to voter set
     * add = false => Remove this member from voter set
     */
    fun proposeVoterSetMemberAsync(voterSet: String, member: String, add: Boolean): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val voterSetGtv = voterSetGtv(voterSet)
        val memberToAddOrRemove = providerGtv(member)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_voter_set_provider",
                    meProvider, voterSetGtv, memberToAddOrRemove, gtv(add)
            )
            sign(buildSigMaker())
        }
    }

    fun proposeVoterSetGovernorAsync(voterSetName: String, newGovernor: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val vs = voterSetGtv(voterSetName)
        val newGovernorGtv = voterSetGtv(newGovernor)
        return makeTransactionWithNop().apply {
            addOperation(
                    "propose_voter_set_governor",
                    meProvider, vs, newGovernorGtv
            )
            sign(buildSigMaker())
        }
    }
}
