package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.chain0.cluster.cluster_op.createClusterOperation
import net.postchain.chain0.common.proposal.proposeBlockchainOperation
import net.postchain.chain0.common.proposal.proposeClusterProviderOperation
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.common.voting.createVoterSetOperation
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.chain0.container.container_op.createContainerFromOperation
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
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

    fun getNodeInfo(key: String): Gtv {
        var returnVal: Gtv? = null
        doInTryBlock {
            val info = getPostchainClient().query(
                    "get_node_data",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            ).asDict().toMutableMap()
            val clusterInfo = getPostchainClient().query(
                    "list_clusters_of_node",
                    gtv("pubkey" to gtv(key.hexStringToByteArray()))
            )
            info["cluster"] = clusterInfo
            returnVal = gtv(info)
        }
        return returnVal!!
    }

    fun getBlockchainConfiguration(blockchainRID: BlockchainRid, height: Long): ByteArray {
        var returnVal: ByteArray? = null
        doInTryBlock {
            // it means current height
            var heightConfiguration = height
            if (height == -1L) {
                heightConfiguration = getPostchainClient().getBlockchainLastHeight(blockchainRID)
            }
            returnVal = getPostchainClient().nmGetBlockchainConfiguration(blockchainRID, heightConfiguration)
        }
        return returnVal!!
    }

    open fun sendTxSync(tx: TransactionBuilder, onSuccess: String, onFail: String) {
        doInTryBlock(false) {
            val txResult = tx.postAwaitConfirmation()
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

    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(
                voteAsync(rowid, yes),
                "Vote added successfully", "Cannot add vote"
        )
    }

    fun proposeClusterProvider(clusterName: String, key: String, add: Boolean) {
        sendTxSync(
                proposeClusterProviderAsync(clusterName, key, add),
                "Cluster $clusterName providers update proposed",
                "Failed proposing cluster $clusterName providers update"
        )
    }

    fun proposeBlockchain(blockchainConfigFile: File, format: String?, container: String, name: String) {
        sendTxSync(
                proposeBlockchainAsync(blockchainConfigFile, format, container, name),
                "Blockchain $name has been proposed",
                "Cannot add bc proposal"
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

    fun proposeProviderIsSystemAsync(pubKey: String, isSystem: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeProviderIsSystemOperation(
                config.pubkey().data,
                pubKey.hexStringToByteArray(),
                isSystem,
                ""
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
                config.pubkey().data, data, name, containerName, ""
        )
    }

    /**
     * Propose update of cluster providers
     * add = true => Add this provider
     * add = false => Remove this provider from cluster
     */
    fun proposeClusterProviderAsync(clusterName: String, provider: String, add: Boolean): TransactionBuilder {
        return makeTransactionWithNop().proposeClusterProviderOperation(
                config.pubkey().data, clusterName, provider.hexStringToByteArray(), add, ""
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
                removeMember?.let { listOf(it.hexStringToByteArray()) } ?: listOf(),
                ""
        )
    }

    fun proposeVoterSetGovernorAsync(voterSetName: String, newGovernor: String): TransactionBuilder {
        return makeTransactionWithNop().proposeUpdateVoterSetOperation(
                config.pubkey().data, voterSetName, null, newGovernor, listOf(), listOf(), ""
        )
    }
}
