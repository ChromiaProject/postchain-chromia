package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.chain0.cluster.cluster_op.createClusterOperation
import net.postchain.chain0.common.addNodeOperation
import net.postchain.chain0.common.proposal.getProposalsSince
import net.postchain.chain0.common.proposal.proposeBlockchainOperation
import net.postchain.chain0.common.proposal.proposeClusterProviderOperation
import net.postchain.chain0.common.proposal.proposeConfigurationAtOperation
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.chain0.common.queries.getBlockchainReplicas
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.chain0.common.queries.getNodesWithProvider
import net.postchain.chain0.common.queries.getProviderClusters
import net.postchain.chain0.common.queries.getProviderData
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.common.voting.createVoterSetOperation
import net.postchain.chain0.common.voting.getVoterSetGovernor
import net.postchain.chain0.common.voting.getVoterSetMembers
import net.postchain.chain0.common.voting.getVoterSets
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.chain0.container.container_op.createContainerFromOperation
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmComputeBlockchainList
import net.postchain.chain0.nm_api.nmGetPeerListVersion
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.TransactionResult
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.RowId
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
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

    fun getClusterProviders(name: String) = getPostchainClient().getClusterProviders(name)

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

    fun getPeerListVersion() = getPostchainClient().nmGetPeerListVersion()

    fun listNodesWithProvider() = getPostchainClient().getNodesWithProvider()


    fun listClustersForProvider(key: String) = getPostchainClient().getProviderClusters(PubKey(key))

    /**
     * key - publicKey of node
     */
    fun listBlockchainsForNode(key: String) = getPostchainClient().nmComputeBlockchainList(key.hexStringToByteArray())

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

    fun listBlockchainSigners(blockchainRID: String): List<Array<out Gtv>> {
        val blockchain = blockchainGtv(blockchainRID)
        return getPostchainClient().getBlockchainSigners(RowId(blockchain.asInteger()))
    }

    fun listVoterSetMembers(name: String) = getPostchainClient().getVoterSetMembers(name)

    fun listVoterSets() = getPostchainClient().getVoterSets()

    fun getVoterSetGovernor(name: String) = getPostchainClient().getVoterSetGovernor(name)

    fun listBlockchainReplicas(blockchainRID: String): List<Array<out Gtv>> {
        val blockchain = blockchainGtv(blockchainRID)
        return getPostchainClient().getBlockchainReplicas(RowId(blockchain.asInteger()))
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

    fun createVoterSet(name: String, providers: String, threshold: Long, governorName: String?) {
        sendTxSync(
                createVoterSetAsync(name, providers, threshold, governorName),
                "voter set created",
                "Cannot create voter set"
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

    fun proposeBlockchain(blockchainConfigFile: String, format: String?, container: String, name: String) {
        sendTxSync(
                proposeBlockchainAsync(File(blockchainConfigFile), format, container, name),
                "Blockchain $name has been proposed",
                "Cannot add bc proposal"
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

    fun registerProviderAsync(key: String, nodeProvider: Boolean): TransactionBuilder {
        return makeTransactionWithNop().registerProviderOperation(
                config.pubkey().data,
                PubKey(key),
                if (nodeProvider) ProviderTier.NODE_PROVIDER else ProviderTier.COMMUNITY_NODE_PROVIDER
        )
    }

    fun createVoterSetAsync(
            name: String,
            providerKeys: String,
            threshold: Long,
            governorName: String?
    ): TransactionBuilder {
        return makeTransactionWithNop().createVoterSetOperation(
                config.pubkey().data, name, threshold, providerKeys.split(",").map { it.hexStringToByteArray() }, governorName
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
        return makeTransactionWithNop().proposeClusterProviderOperation(
                config.pubkey().data,
                clusterName,
                key.hexStringToByteArray(),
                true
        )
    }

    /** Propose a new (isolated) container with default resource limits and a deployer voter set in an existing cluster.
     * Who can create a container and update resource limits? Cluster's deployer voter set.
     * */
    fun createContainerAsync(containerName: String, clusterName: String, deployerName: String): TransactionBuilder {
        return makeTransactionWithNop().createContainerFromOperation(config.pubkey().data, containerName, clusterName, 1, deployerName)
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

    fun proposeProviderIsSystemAsync(pubKey: String, isSystem: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeProviderIsSystemOperation(
                config.pubkey().data,
                pubKey.hexStringToByteArray(),
                isSystem
        )
    }

    fun proposeEnableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop()
                .proposeProviderStateOperation(config.pubkey().data, key.hexStringToByteArray(), true)
    }

    fun revokeProposalAsync(rowid: Long): TransactionBuilder {
        return makeTransactionWithNop().addOperation(
                "revoke_proposal", gtv(config.signers.first().pubKey.hex()), gtv(rowid)
        )
    }

    fun proposeDisableProviderAsync(key: String): TransactionBuilder {
        return makeTransactionWithNop()
                .proposeProviderStateOperation(config.pubkey().data, key.hexStringToByteArray(), false)
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
