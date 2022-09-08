package net.postchain.mc.cli.enterprise0

import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.mc.cli.common0.CliExecution
import java.io.File

class CliExecutionE0(config: PostchainClientConfig) : CliExecution(config) {

    companion object : KLogging()

    fun proposeConfigurationInternal(blockchainRID: String, blockchainConfigFile: File, height: Long, format: String?)
            : TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return makeTransactionWithNop().addOperation("propose_configuration",
                    gtv(blockchainRID.hexStringToByteArray()), gtv(config.signers.first().pubKey.key), gtv(data), gtv(height))
    }
    fun proposeConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)  {
        sendTxSync(proposeConfigurationInternal(blockchainRID, File(blockchainConfigFile), height, format),
                "proposal of config added", "Cannot add config proposal")

    }

    fun proposeProvider(key: String) {
        sendTxSync(proposeProviderInternal(key), "proposed provider was added successfully!",
                "Cannot add provider proposal")
    }

    fun proposeProviderInternal(key: String) : TransactionBuilder {
        return makeTransactionWithNop().addOperation("propose_provider",
                    gtv(config.signers.first().pubKey.key), gtv(key.hexStringToByteArray()))
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a block signing
     * node can vote for a pending configuration.
     */
    fun voteInternal(rowid: Long, yes: Boolean) : TransactionBuilder {
        return makeTransactionWithNop().addOperation("make_vote",
                    gtv(config.signers.first().pubKey.key), gtv(rowid), gtv((yes)))
    }
    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(voteInternal(rowid, yes), "vote added successfully", "Cannot add vote")
    }

    fun proposeEnableProviderInternal(key: String) : TransactionBuilder{
        val providerToBeEnabled = providerGtv(key)
        return makeTransactionWithNop().addOperation("propose_enable_provider", gtv(config.signers.first().pubKey.key), providerToBeEnabled)
    }

    fun proposeEnableProvider(key: String) {
        sendTxSync(proposeEnableProviderInternal(key), "Enabling of provider has been proposed",
                "Cannot propose enabling of provider")
    }

    fun proposeBlockchain(blockchainConfigFile: String, nodes: String, format: String?) {
        sendTxSync(proposeBlockchainInternal(File(blockchainConfigFile), nodes, format), "Blockchain has been proposed",
                "Cannot add bc proposal")
    }

    fun proposeBlockchainInternal(blockchainConfigFile: File, nodes: String, format: String?) : TransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return proposeBc(nodes, data)
    }

    fun proposeBlockchainGtvInternal(blockchainConfig: Gtv, nodes: String): TransactionBuilder {
        val data = GtvEncoder.encodeGtv(blockchainConfig)
        return proposeBc(nodes, data)
    }

    private fun proposeBc(nodes: String, data: ByteArray): TransactionBuilder {
        val nodeList = nodes.split(",").map { gtv(it) }
        return makeTransactionWithNop().addOperation("propose_blockchain",
                    gtv(config.signers.first().pubKey.key), gtv(data), gtv(nodeList))
    }

    fun proposeDisableProviderInternal(key: String) : TransactionBuilder {
        val providerToBeDisabled = providerGtv(key)
        return makeTransactionWithNop().addOperation("propose_disable_provider", gtv(config.signers.first().pubKey.key), providerToBeDisabled)
    }

    fun proposeDisableProvider(key: String) {
        sendTxSync(proposeDisableProviderInternal(key), "Disabling of provider has been proposed",
                "Cannot propose disabling of provider")
    }

    fun proposeAddBlockchainSignersInternal(blockchainRID: String, signers: String) : TransactionBuilder {
        val nodeList = signers.split(",").map { gtv(it) }
        return makeTransactionWithNop().addOperation("propose_add_blockchain_signers", gtv(config.signers.first().pubKey.key), gtv(blockchainRID.hexStringToByteArray()), gtv(nodeList))
    }
    fun ProposeAddBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(proposeAddBlockchainSignersInternal(blockchainRID,signers),
                "proposal of new signers added successfully",
                "cannot add proposal of new signers for blockchain")
    }

    fun proposeStopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean) : TransactionBuilder {
        return makeTransactionWithNop().addOperation("propose_stop_blockchain",
                    gtv(config.signers.first().pubKey.key), gtv(blockchainRID.hexStringToByteArray()), gtv(removeReplicas))
    }

    fun proposeStopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(proposeStopBlockchainInternal(blockchainRID, removeReplicas),
                "blockchain stop proposition was added successfully",
                "Cannot add proposal for stopping blockchain")
    }

    fun proposeRemoveBlockchainSignersInternal(blockchainRID: String, signers: String) : TransactionBuilder {
        val nodeList = signers.split(",").map { gtv(it) }
        return makeTransactionWithNop().addOperation("propose_remove_blockchain_signers",
                    gtv(config.signers.first().pubKey.key), gtv(blockchainRID.hexStringToByteArray()), gtv(nodeList))
    }
    fun proposeRemoveBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(proposeRemoveBlockchainSignersInternal(blockchainRID, signers),
                "Proposal on removing signers added",
                "Cannot ad proposal of removing signers from blockchain")
    }




    /**
     * This operation initializes the database with a first provider. If table `providers` is empty, the public key from
     * the module argument is registered as a first provider and enabled. Why? The system needs at least one provider,
     * that can vote for update propsals.
     */
    fun initInternal() : TransactionBuilder {
        return makeTransactionWithNop().addOperation("init")
    }
    fun init() {
        sendTxSync(initInternal(), "Initial provider added and enabled",
                "Cannot add and enable initial provider")
    }



    fun updateProvider(key: String, name: String) {
        sendTxSync(updateProviderInternal(key, name), "Provider data has been updated",
                "Cannot update provider")
    }

    fun updateProviderInternal(key: String, name: String): TransactionBuilder {
        val provider = providerGtv(key)
        var data: Array<Gtv> = arrayOf(provider)
        if (name.isNotEmpty()) {
            data = data.plus(gtv(name))
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().addOperation("update_provider_data", *data)
    }

    fun listProposalsSince(rowid: Long) : List<Gtv> {
        val returnList = arrayListOf<Gtv>()
        doInTryBlock {
            val list =  getPostchainClient().querySync("get_proposals_since", gtv(
                    "since" to gtv(rowid)))
                    .asArray()
            returnList.addAll(list.map { it })
        }
        return returnList
    }

    fun getProposal(rowid: Long) : Gtv {
        var returnValue : Gtv? = null
        doInTryBlock {
            val prop =  getPostchainClient().querySync("get_proposal", gtv(
                    "rowid" to gtv(rowid)))
            returnValue = prop
        }
        return returnValue!!
    }

}