package net.postchain.mc.cli.common0

import mu.KLogging
import net.postchain.chain0.common.proposal.proposeBlockchainOperation
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
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
        return makeTransactionWithNop().proposeBlockchainOperation(
                config.pubkey().data, data, name, container, ""
        )
    }

}
