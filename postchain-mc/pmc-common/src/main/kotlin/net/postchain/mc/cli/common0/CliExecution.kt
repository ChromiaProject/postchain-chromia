package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.chain0.common.*
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.TransactionResult
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.readConfigurationFile
import java.io.File

open class CliExecution(val config: PostchainClientConfig) {

    companion object : KLogging()

    open fun getPostchainClient() = ClientUtil.fromConfig(config)

    protected fun makeTransactionWithNop(): TransactionBuilder {
        return getPostchainClient().transactionBuilder().addNop()
    }

    private fun doInTryBlock(logError: Boolean = true, todo: () -> Unit) {
        try {
            todo()
        } catch (e: Exception) {
            if (logError) {
                logger.error { e.message }
            }
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

    fun getClusterInfo(name: String): Gtv? {
        var info: Gtv? = null

        doInTryBlock {
            info = getPostchainClient().querySync(
                    "get_cluster_data",
                    gtv("name" to gtv(name))
            )
        }

        return info
    }

    fun getClusterProviders(name: String): List<Gtv> {
        val providers = mutableListOf<Gtv>()

        doInTryBlock {
            getPostchainClient().querySync(
                    "get_cluster_providers",
                    gtv("name" to gtv(name))
            ).asArray().forEach(providers::add)
        }

        return providers
    }

    fun getClusterNodes(name: String): List<Gtv> {
        val nodes = mutableListOf<Gtv>()

        doInTryBlock {
            getPostchainClient().querySync(
                    "get_cluster_nodes",
                    gtv("name" to gtv(name))
            ).asArray().forEach(nodes::add)
        }

        return nodes
    }

    fun listProvidersActionPoints(key: String): Long {
        var points: Gtv? = null

        doInTryBlock {
            points = getPostchainClient().querySync(
                    "get_provider_points",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )
        }

        return points!!.asInteger()
    }

    fun getNodeInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().querySync(
                    "get_node_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).asDict().toMutableMap()
            val clusterInfo = getPostchainClient().querySync(
                    "list_clusters_of_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )
            info["cluster"] = clusterInfo
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
            val height = getPostchainClient().querySync(
                    "get_blockchain_last_height",
                    gtv("blockchain_rid" to gtv(blockchainRID.hexStringToByteArray()))
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

    fun listClusterLimits(name: String): Map<String, Long> {
        var listLimits = mapOf<String, Long>()
        doInTryBlock {
            val d = getPostchainClient().querySync(
                    "nm_get_cluster_limits",
                    gtv("name" to gtv(name))
            )
                    .asDict()
            listLimits = d.mapValues { it.value.asInteger() }
        }
        return listLimits
    }


    fun listContainerLimits(name: String): Map<String, Long> {
        var listLimits = mapOf<String, Long>()
        doInTryBlock {
            val d = getPostchainClient().querySync(
                    "nm_get_container_limits",
                    gtv("name" to gtv(name))
            )
                    .asDict()
            listLimits = d.mapValues { it.value.asInteger() }
        }
        return listLimits
    }

    /**
     * key - publicKey of node
     */
    fun listContainersForNode(key: String): List<Gtv> {
        val containers = arrayListOf<Gtv>()

        doInTryBlock {
            val isNode = getPostchainClient().querySync(
                    "is_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).asBoolean()

            if (isNode) {
                getPostchainClient().querySync(
                        "get_node_containers",
                        gtv("pubkey" to gtv(key.hexStringToByteArray()))
                ).asArray().forEach(containers::add)
            }
        }

        return containers
    }

    fun listContainers(): List<Gtv> {
        val containers = arrayListOf<Gtv>()
        doInTryBlock {
            getPostchainClient().querySync("get_containers")
                    .asArray().forEach(containers::add)
        }
        return containers
    }

    fun listClusterContainers(cluster: String): List<Gtv> {
        val containers = arrayListOf<Gtv>()
        doInTryBlock {
            getPostchainClient().querySync(
                    "get_cluster_containers",
                    gtv("cluster_name" to gtv(cluster))
            ).asArray().forEach(containers::add)
        }
        return containers
    }

    fun listClustersForProvider(key: String): List<String> {
        val listClusters = arrayListOf<String>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_provider_clusters", gtv(
                    "pubkey" to gtv(key.hexStringToByteArray())
            )
            ).asArray()
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

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForContainer(name: String): List<ByteArray> {
        val listBlockChain = arrayListOf<ByteArray>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "nm_get_blockchains_for_container", gtv(
                    "container_name" to GtvString(name)
            )
            ).asArray()
            listBlockChain.addAll(list.map { it.asByteArray() })
        }
        return listBlockChain
    }

    fun getContainerForBlockchain(blockchainRid: String): String? {
        var container: String? = null
        doInTryBlock {
            container = getPostchainClient().querySync(
                    "nm_get_container_for_blockchain",
                    gtv("blockchain_rid" to gtv(blockchainRid.hexStringToByteArray()))
            ).asString()
        }
        return container
    }

    fun listBlockchainDependencies(blockchainRID: String, height: Long): List<Pair<ByteArray, String>> {
        val listBlockChainContainerPair = arrayListOf<Pair<ByteArray, String>>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().querySync(
                    "nm_get_blockchain_dependencies",
                    gtv(
                            "blockchain" to gtv(blockchain.asInteger()),
                            "height" to GtvInteger(height)
                    )
            )

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
        return getPostchainClient().getBlockchains(includeInactive).map { it.rid }
    }

    fun listBlockchainSigners(blockchainRID: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val blockchain = blockchainGtv(blockchainRID)
            val list = getPostchainClient().querySync(
                    "get_blockchain_signers",
                    gtv("bc" to gtv(blockchain.asInteger()))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listVoterSetMembers(name: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_voter_set_members",
                    gtv("name" to GtvString(name))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listVoterSets(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync("list_voter_sets").asArray()
            returnList.addAll(list.map { it["name"]!! })
        }
        return returnList
    }

    fun getVoterSetGovernor(name: String): String {
        var returnValue = ""
        doInTryBlock {
            val governorName = getPostchainClient().querySync(
                    "get_voter_set_governor",
                    gtv("name" to GtvString(name))
            )

                    .asString()
            returnValue = governorName
        }
        return returnValue
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


    fun listContainerReplicas(containerName: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val container = containerGtv(containerName)
            val list = getPostchainClient().querySync(
                    "get_container_replicas",
                    gtv("container" to gtv(container.asInteger()))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listNodesByProvider(key: String): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_nodes_by_provider",
                    gtv("provider_key" to gtv(key.hexStringToByteArray()))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listClusters(): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "list_clusters",
                    gtv("type" to gtv("list_clusters"))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listProposalsSince(rowid: Long): List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list = getPostchainClient().querySync(
                    "get_proposals_since", gtv(
                    "since" to gtv(rowid)
            )
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    open fun sendTxUnconfirmed(tx: TransactionBuilder): TransactionResult {
        return tx.postSync()
    }

    open fun sendTxSync(tx: TransactionBuilder, onSuccess: String, onFail: String) {
        doInTryBlock(false) {
            val txResult = tx.postSyncAwaitConfirmation()
            when (txResult.status) {
                TransactionStatus.CONFIRMED -> println(onSuccess)
                TransactionStatus.REJECTED -> println(onFail + ": " + txResult.rejectReason)
                else -> println(onFail)
            }
        }
    }

    fun registerProvider(key: String, tier: Long) {
        sendTxSync(
                registerProviderAsync(key, tier),
                "Provider has been registered",
                "Cannot register provider"
        )
    }

    fun createVoterSet(name: String, providers: String, threshold: Long, governorName: String?) {
        sendTxSync(
                createVoterSetAsync(name, providers, threshold, governorName),
                "voter set created",
                "Cannot create voter set"
        )
    }

    fun transferActionPoints(to: String, amount: Long) {
        sendTxSync(
                transferActionPointsAsync(to, amount),
                "Action points transferred",
                "Transferring action points failed"
        )
    }

    fun addNode(key: String, host: String, port: Long, apiUrl: String, clusterName: String) {
        sendTxSync(
                addNodeAsync(key, host, port, apiUrl, clusterName),
                "Node has been enabled",
                "Cannot add node"
        )
    }

    fun addBlockchainReplica(blockchainRID: String, key: String) {
        sendTxSync(
                addBlockchainReplicaAsync(blockchainRID, key),
                "Replica added",
                "Cannot add replica"
        )
    }

    fun addContainerReplica(clusterName: String, containerName: String) {
        sendTxSync(
                addBlockchainReplicaAsync(clusterName, containerName),
                "Replica added",
                "Cannot add replica"
        )
    }

    fun removeBlockchainReplica(blockchainRID: String, key: String) {
        sendTxSync(
                removeBlockchainReplicaAsync(blockchainRID, key),
                "Replica removed",
                "Cannot remove replica node"
        )
    }

    fun removeContainerReplica(clusterName: String, containerName: String) {
        sendTxSync(
                removeContainerReplicaAsync(clusterName, containerName),
                "Replica removed",
                "Cannot remove replica"
        )
    }

    fun removeNode(key: String) {
        sendTxSync(
                removeNodeAsync(key),
                "Node removed",
                "Cannot remove node"
        )
    }

    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(
                voteAsync(rowid, yes),
                "Vote added successfully", "Cannot add vote"
        )
    }

    fun revokeProposal(rowid: Long) {
        sendTxSync(
                revokeProposalAsync(rowid),
                "Proposal revoked successfully", "Cannot revoke proposal"
        )
    }

    fun proposeEnableProvider(key: String) {
        sendTxSync(
                proposeEnableProviderAsync(key),
                "Enabling of provider has been proposed",
                "Cannot propose enabling of provider"
        )
    }

    fun proposeDisableProvider(key: String) {
        sendTxSync(
                proposeDisableProviderAsync(key),
                "Disabling of provider has been proposed",
                "Cannot propose disabling of provider"
        )
    }

    fun proposeClusterLimits(clusterName: String, limitMap: Map<String, Long>) {
        sendTxSync(
                proposeClusterLimitsAsync(clusterName, limitMap),
                "Cluster limits proposed",
                "Failed proposing new cluster limits"
        )
    }

    fun proposeClusterProvider(clusterName: String, key: String, add: Boolean) {
        sendTxSync(
                proposeClusterProviderAsync(clusterName, key, add),
                "Cluster providers update proposed",
                "Failed proposing cluster providers update"
        )
    }

    fun proposeClusterDeployer(clusterName: String, key: String) {
        sendTxSync(
                proposeClusterDeployerAsync(clusterName, key),
                "Cluster deployer update proposed",
                "Failed proposing cluster deployer"
        )
    }

    fun proposeVoterSetGovernor(name: String, new: String) {
        sendTxSync(
                proposeVoterSetGovernorAsync(name, new),
                "Voter set governor update proposed",
                "Failed proposing voter set governor"
        )
    }

    fun proposeVoterSetMember(voterSet: String, member: String, add: Boolean) {
        sendTxSync(
                proposeVoterSetMemberAsync(voterSet, member, add),
                "Voter set member update proposed",
                "Failed proposing voter set member update"
        )
    }

    fun proposeContainerLimits(containerName: String, limitMap: Map<String, Long>) {
        sendTxSync(
                proposeContainerLimitsAsync(containerName, limitMap),
                "Container limits proposed",
                "Failed proposing new container limits"
        )
    }

    fun proposeContainer(containerName: String, clusterName: String, deployerName: String) {
        sendTxSync(
                proposeContainerAsync(containerName, clusterName, deployerName),
                "Container proposed",
                "Failed proposing new container"
        )
    }

    fun proposeBlockchain(blockchainConfigFile: String, format: String?, container: String, name: String) {
        sendTxSync(
                proposeBlockchainAsync(File(blockchainConfigFile), format, container, name),
                "Blockchain has been proposed",
                "Cannot add bc proposal"
        )
    }

    private fun voterSetGtv(name: String): Gtv {
        return getPostchainClient().querySync(
                "get_voter_set", gtv(
                "name" to gtv(name)
        )
        )
    }

    fun clusterGtv(name: String): Gtv {
        return getPostchainClient().querySync(
                "get_cluster", gtv(
                "name" to gtv(name)
        )
        )
    }

    fun containerGtv(name: String): Gtv {
        return getPostchainClient().querySync(
                "get_container", gtv(
                "name" to gtv(name)
        )
        )
    }

    //comma separeted list of providers
    fun providersGtv(keys: String): Gtv {
        val gtvList = keys.split(",").map {
            getPostchainClient().querySync(
                    "get_provider",
                    gtv("pubkey" to gtv(it.hexStringToByteArray()))
            )
        }
        return gtv(gtvList)
    }

    //Single provider
    fun providerGtv(key: String): Gtv {
        return getPostchainClient().querySync(
                "get_provider", gtv(
                "pubkey" to gtv(key.hexStringToByteArray())
        )
        )
    }

    fun nodeGtv(key: String): Gtv {
        return getPostchainClient().querySync(
                "get_node",
                gtv("pubkey" to gtv(key.hexStringToByteArray()))
        )
    }

    fun blockchainGtv(blockchainRID: String): Gtv {
        return getPostchainClient().querySync(
                "get_blockchain",
                gtv("rid" to gtv(blockchainRID.hexStringToByteArray()))
        )
    }

    /**
     * Below: Asynchronous versions of operation commands. These are the ones tested in DirectoryTest.kt. They are given
     * a synchronizing skin so that they can be called by the client. Example: CommandAddNode calls addNode() that calls
     * AddNodeAsync().
     */

    fun registerProviderAsync(key: String, tier: Long): TransactionBuilder {
        return makeTransactionWithNop().registerProviderOperation(config.pubkey().key, key.hexStringToByteArray(), tier)
    }

    fun createVoterSetAsync(
            name: String,
            providerKeys: String,
            threshold: Long,
            governorName: String?
    ): TransactionBuilder {
        val meProvider = providerGtv(config.signers.first().pubKey.hex())
        var providerList: Gtv
        if (providerKeys.isEmpty()) {
            providerList = GtvNull
        } else {
            providerList = providersGtv(providerKeys)
        }
        var governor: Gtv
        if (governorName == null || governorName.isEmpty()) {
            governor = GtvNull
        } else {
            governor = voterSetGtv(governorName)
        }
        return makeTransactionWithNop().addOperation(
                "create_voter_set",
                meProvider, GtvString(name), GtvInteger(threshold), providerList, governor
        )
    }

    /** Add new node. Optionally, also add it to a cluster */
    fun addNodeAsync(key: String, host: String, port: Long, apiUrl: String, clusterName: String): TransactionBuilder {
        return makeTransactionWithNop().addNodeOperation(config.signers.first().pubKey.key, key.hexStringToByteArray(), host, port, apiUrl, if (clusterName == "") listOf() else listOf(clusterName))
    }

    /** Add existing provider to existing cluster
     * */
    fun addProviderToClusterAsync(key: String, clusterName: String): TransactionBuilder {
        return makeTransactionWithNop().addProviderToClusterOperation(config.pubkey().key, key.hexStringToByteArray(), clusterName)
    }

    /** Add existing node to existing cluster
     * */
    fun addNodeToClusterAsync(key: String, clusterName: String): TransactionBuilder {
        val provider = config.signers.first().pubKey.key
        return makeTransactionWithNop().addNodeToClusterOperation(
                provider,
                key.hexStringToByteArray(), clusterName
        )
    }

    /** Propose a new container resource limits.
     * Who can update container resource limits? Cluster's deployer voter set.
     * */
    fun proposeContainerLimitsAsync(containerName: String, limits: Map<String, Long>): TransactionBuilder {
        val currentLimits = listContainerLimits(containerName).toMutableMap()
        currentLimits.putAll(limits)
        return makeTransactionWithNop().proposeContainerLimitsOperation(config.pubkey().key, containerName, currentLimits["ram"]!!, currentLimits["cpu"]!!, currentLimits["storage"]!!)
    }

    /** Propose a new (isolated) container with default resource limits and a deployer voter set in an existing cluster.
     * Who can create a container and update resource limits? Cluster's deployer voter set.
     * */
    fun proposeContainerAsync(containerName: String, clusterName: String, deployerName: String): TransactionBuilder {
        return makeTransactionWithNop().proposeContainerOperation(config.pubkey().key, clusterName, containerName, deployerName)
    }

    /** Propose new cluster resource limits.
     * Who can update cluster limits? Cluster governance voter set.
     * */
    fun proposeClusterLimitsAsync(clusterName: String, limits: Map<String, Long>): TransactionBuilder {
        val currentLimits = listClusterLimits(clusterName).toMutableMap()
        currentLimits.putAll(limits)
        return makeTransactionWithNop().proposeClusterLimitsOperation(
            config.pubkey().key, clusterName, currentLimits["ram"]!!, currentLimits["cpu"]!!, currentLimits["storage"]!!
        )
    }

    fun transferActionPointsAsync(to: String, amount: Long): TransactionBuilder {
        val meProvider = providerGtv(config.signers.first().pubKey.hex())
        val toProvider = providerGtv(to)
        return makeTransactionWithNop().addOperation("transfer_action_points", meProvider, toProvider, gtv(amount))
    }

    fun createClusterAsync(
            newClusterName: String,
            providerKeys: String?,
            governorSet: String,
            deployerSet: String
    ): TransactionBuilder {
        return makeTransactionWithNop().createClusterOperation(config.pubkey().key, newClusterName, providerKeys?.split(",")?.map { it.hexStringToByteArray() }, governorSet, deployerSet)
    }

    fun addBlockchainReplicaAsync(blockchainRID: String, key: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().addOperation("add_bc_replica", provider, blockchain, node)
    }

    fun addContainerReplicaAsync(clusterName: String, containerName: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().addOperation("add_container_replica", provider, cluster, container)
    }

    fun removeBlockchainReplicaAsync(blockchainRID: String, key: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val blockchain = blockchainGtv(blockchainRID)
        val node = nodeGtv(key)
        return makeTransactionWithNop().addOperation("remove_bc_replica", provider, blockchain, node)
    }

    fun removeContainerReplicaAsync(clusterName: String, containerName: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        val cluster = clusterGtv(clusterName)
        val container = containerGtv(containerName)
        return makeTransactionWithNop().addOperation("remove_bc_replica", provider, cluster, container)
    }

    fun removeNodeAsync(key: String): TransactionBuilder {
        val provider = providerGtv(config.signers.first().pubKey.hex())
        return makeTransactionWithNop().addOperation(
                "remove_node",
                provider, gtv(key.hexStringToByteArray())
        )
    }

    fun proposeProviderIsSystemAsync(pubKey: String, isSystem: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeProviderIsSystemOperation(config.pubkey().key, pubKey.hexStringToByteArray(), isSystem)
    }

    fun proposeEnableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop().proposeEnableProviderOperation(config.pubkey().key, key.hexStringToByteArray())
    }

    fun revokeProposalAsync(rowid: Long): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                "revoke_proposal", gtv(config.signers.first().pubKey.hex()), gtv(rowid)
        )
    }

    fun proposeDisableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop().proposeDisableProviderOperation(config.pubkey().key, key.hexStringToByteArray())
    }

    fun proposeConfigurationAsync(
            blockchainRID: String,
            blockchainConfigFile: File,
            height: Long,
            format: String?,
            force: Boolean
    ): TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return makeTransactionWithNop().proposeConfigurationAtOperation(
            config.pubkey().key, blockchainRID.hexStringToByteArray(), data, height, force
        )
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteAsync(rowid: Long, yes: Boolean): TransactionBuilder {
        return makeTransactionWithNop().makeVoteOperation(config.pubkey().key, rowid, yes)
    }

    /**
     * Propose add Blockchain to an existing container
     */
    fun proposeBlockchainAsync(blockchainConfigFile: File, format: String?, container: String, name: String): TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return proposeBc(data, container, name)
    }

    fun proposeBlockchainGtvAsync(blockchainConfig: Gtv, container: String, name: String): TransactionBuilder {
        val data = GtvEncoder.encodeGtv(blockchainConfig)
        return proposeBc(data, container, name)
    }

    private fun proposeBc(data: ByteArray, containerName: String, name: String): TransactionBuilder {
        return makeTransactionWithNop().proposeBlockchainOperation(
                config.pubkey().key, data, name, containerName
        )
    }

    /**
     * Propose update of cluster providers
     * add = true => Add this provider
     * add = false => Remove this provider from cluster
     */
    fun proposeClusterProviderAsync(clusterName: String, provider: String, add: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeClusterProviderOperation(
            config.pubkey().key, clusterName, provider.hexStringToByteArray(), add
        )
    }

    fun proposeClusterDeployerAsync(clusterName: String, newDeployer: String): TransactionBuilder {
        return makeTransactionWithNop().proposeClusterDeployerOperation(
            config.pubkey().key, clusterName, newDeployer
        )
    }

    /**
     * Propose update a voter set's members
     * add = true => Add this provider as member to voter set
     * add = false => Remove this member from voter set
     */
    fun proposeVoterSetMemberAsync(voterSet: String, member: String, add: Boolean): TransactionBuilder {
        val newMember = if (add) member else null
        val removeMember = if (!add) member else null
        return makeTransactionWithNop().proposeUpdateVoterSetOperation(
            config.pubkey().key, voterSet, null, null, newMember?.let { listOf(it.hexStringToByteArray()) } ?: listOf(), removeMember?.let { listOf(it.hexStringToByteArray()) } ?: listOf()
        )
    }

    fun proposeVoterSetGovernorAsync(voterSetName: String, newGovernor: String): TransactionBuilder {
        return makeTransactionWithNop().proposeUpdateVoterSetOperation(
            config.pubkey().key, voterSetName, null, newGovernor, listOf(), listOf()
        )
    }
}
