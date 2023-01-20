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
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteAsync(rowid: Long, yes: Boolean): TransactionBuilder {
        return makeTransactionWithNop().makeVoteOperation(config.pubkey().data, rowid, yes)
    }

}
