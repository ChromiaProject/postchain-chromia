package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.chain0.cluster.cluster_op.createClusterOperation
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.chain0.common.addNodeOperation
import net.postchain.chain0.common.cluster.addNodeToClusterOperation
import net.postchain.chain0.common.cluster.addProviderToClusterOperation
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.common.voting.getVoterSetGovernor
import net.postchain.chain0.common.voting.getVoterSetMembers
import net.postchain.chain0.common.voting.getVoterSets
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.chain0.container.container_op.createContainerFromOperation
import net.postchain.chain0.nm_api.*
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.TransactionResult
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.RowId
import net.postchain.crypto.PubKey
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
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

    fun getProviderInfo(key: String) = getPostchainClient().getProviderData(PubKey(key))

    fun getClusterInfo(name: String) = getPostchainClient().getClusterData(name)

    fun getClusterProviders(name: String) = getPostchainClient().getClusterProviders(name)

    fun listProvidersActionPoints(key: String) = getPostchainClient().getProviderPoints(PubKey(key))

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

    fun getBlockchainLastHeight(blockchainRID: String) = getPostchainClient().getBlockchainLastHeight(BlockchainRid.buildFromHex(blockchainRID))

    fun getNodeListVersion() = getPostchainClient().nmGetPeerListVersion()

    fun listNodesWithProvider() = getPostchainClient().getNodesWithProvider()

    fun listClusterLimits(name: String) = getPostchainClient().nmGetClusterLimits(name)


    fun listContainerLimits(name: String) = getPostchainClient().nmGetContainerLimits(name)

    /**
     * key - publicKey of node
     */
    fun listContainersForNode(key: String) = getPostchainClient().getNodeContainers(PubKey(key))

    fun listContainers() = getPostchainClient().getContainers()

    fun listClusterContainers(cluster: String) = getPostchainClient().getClusterContainers(cluster)

    fun listClustersForProvider(key: String) = getPostchainClient().getProviderClusters(PubKey(key))

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String) = getPostchainClient().nmComputeBlockchainList(key.hexStringToByteArray())

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForContainer(name: String) = getPostchainClient().nmGetBlockchainsForContainer(name)

    fun getContainerForBlockchain(blockchainRid: String) = getPostchainClient().nmGetContainerForBlockchain(BlockchainRid.buildFromHex(blockchainRid))

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
        return getPostchainClient().getBlockchains(includeInactive).map { it.rid.data }
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

    fun listVoterSetMembers(name: String) = getPostchainClient().getVoterSetMembers(name)

    fun listVoterSets() = getPostchainClient().getVoterSets()

    fun getVoterSetGovernor(name: String) = getPostchainClient().getVoterSetGovernor(name)

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
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )

                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun listClusters() = getPostchainClient().listClusters()

    fun listProposalsSince(rowid: Long) = getPostchainClient().getProposalsSince(RowId(rowid))

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

    fun proposeClusterProvider(clusterName: String, key: String, add: Boolean) {
        sendTxSync(
                proposeClusterProviderAsync(clusterName, key, add),
                "Cluster providers update proposed",
                "Failed proposing cluster providers update"
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
        return makeTransactionWithNop().registerProviderOperation(
                config.pubkey().data,
                PubKey(key),
                tier
        )
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
        return makeTransactionWithNop().addNodeOperation(
                config.pubkey().data,
                key.hexStringToByteArray(),
                host,
                port,
                apiUrl,
                if (clusterName == "") listOf() else listOf(clusterName)
        )
    }

    /** Add existing provider to existing cluster
     * */
    fun addProviderToClusterAsync(key: String, clusterName: String): TransactionBuilder {
        return makeTransactionWithNop().addProviderToClusterOperation(
                config.pubkey().data,
                key.hexStringToByteArray(),
                clusterName
        )
    }

    /** Add existing node to existing cluster
     * */
    fun addNodeToClusterAsync(key: String, clusterName: String): TransactionBuilder {
        val provider = config.pubkey().data
        return makeTransactionWithNop().addNodeToClusterOperation(
                provider,
                key.hexStringToByteArray(), clusterName
        )
    }

    /** Propose a new (isolated) container with default resource limits and a deployer voter set in an existing cluster.
     * Who can create a container and update resource limits? Cluster's deployer voter set.
     * */
    fun createContainerAsync(containerName: String, clusterName: String, deployerName: String): TransactionBuilder {
        return makeTransactionWithNop().createContainerFromOperation(config.pubkey().data, containerName, clusterName, 1, deployerName)
    }

    fun transferActionPointsAsync(to: String, amount: Long): TransactionBuilder {
        val meProvider = providerGtv(config.signers.first().pubKey.hex())
        val toProvider = providerGtv(to)
        return makeTransactionWithNop().addOperation("transfer_action_points", meProvider, toProvider, gtv(amount))
    }

    fun createClusterAsync(
            newClusterName: String,
            providerKeys: String,
            governorSet: String
    ): TransactionBuilder {
        return makeTransactionWithNop().createClusterOperation(config.pubkey().data, newClusterName, governorSet, providerKeys.split(",").map { it.hexStringToByteArray() })

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
        return makeTransactionWithNop().proposeProviderIsSystemOperation(
                config.pubkey().data,
                pubKey.hexStringToByteArray(),
                isSystem
        )
    }

    fun proposeEnableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop().proposeEnableProviderOperation(config.pubkey().data, key.hexStringToByteArray())
    }

    fun revokeProposalAsync(rowid: Long): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                "revoke_proposal", gtv(config.signers.first().pubKey.hex()), gtv(rowid)
        )
    }

    fun proposeDisableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop().proposeDisableProviderOperation(
                config.pubkey().data,
                key.hexStringToByteArray()
        )
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
                config.pubkey().data, BlockchainRid.buildFromHex(blockchainRID), data, height, force
        )
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteAsync(rowid: Long, yes: Boolean): TransactionBuilder {
        return makeTransactionWithNop().makeVoteOperation(config.pubkey().data, rowid, yes)
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
                config.pubkey().data, data, name, containerName
        )
    }

    /**
     * Propose update of cluster providers
     * add = true => Add this provider
     * add = false => Remove this provider from cluster
     */
    fun proposeClusterProviderAsync(clusterName: String, provider: String, add: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeClusterProviderOperation(
                config.pubkey().data, clusterName, provider.hexStringToByteArray(), add
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
                config.pubkey().data,
                voterSet,
                null,
                null,
                newMember?.let { listOf(it.hexStringToByteArray()) } ?: listOf(),
                removeMember?.let { listOf(it.hexStringToByteArray()) } ?: listOf()
        )
    }

    fun proposeVoterSetGovernorAsync(voterSetName: String, newGovernor: String): TransactionBuilder {
        return makeTransactionWithNop().proposeUpdateVoterSetOperation(
                config.pubkey().data, voterSetName, null, newGovernor, listOf(), listOf()
        )
    }
}
