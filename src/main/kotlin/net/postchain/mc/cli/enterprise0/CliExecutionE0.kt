package net.postchain.mc.cli.enterprise0

import mu.KLogging
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.*
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.ClientConfig

class CliExecutionE0(config: ClientConfig) : CliExecution(config) {

    companion object : KLogging()

    fun proposeConfigurationInternal(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)
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
    fun proposeConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)  {
        sendTxSync(proposeConfigurationInternal(blockchainRID, blockchainConfigFile, height, format),
                "proposal of config added", "Cannot add config proposal")

    }

    fun proposeProvider(key: String, systemProvider: Boolean, tier: Long, clusterName: String) {
        sendTxSync(proposeProviderInternal(key, systemProvider, tier, clusterName), "proposed provider was added successfully!",
                "Cannot add provider proposal")
    }

    fun proposeProviderInternal(key: String, isSystem: Boolean = false, tier: Long = 0L, clusterName: String) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        val cluster = clusterGtv(clusterName)
        return makeTransactionWithNop().apply {
            addOperation("propose_provider",
                    arrayOf(provider, GtvFactory.gtv(key.hexStringToByteArray()), GtvFactory.gtv(isSystem), GtvInteger(tier), cluster))
            sign(buildSigMaker())
        }
    }

    /**
     * Instead of an admin node, configuration changes are made via propositions and voting. This is how a provider
     * can vote for a pending configuration.
     */
    fun voteInternal(rowid: Long, yes: Boolean) : GTXTransactionBuilder {
        val provider = providerGtv(config.pubKey)
        return makeTransactionWithNop().apply {
            addOperation("make_vote",
                    arrayOf(provider, GtvFactory.gtv(rowid), GtvFactory.gtv((yes))))
            sign(buildSigMaker())
        }
    }
    fun vote(rowid: Long, yes: Boolean) {
        sendTxSync(voteInternal(rowid, yes), "vote added successfully", "Cannot add vote")
    }

    fun proposeEnableProviderInternal(key: String) : GTXTransactionBuilder{
        val meProvider = providerGtv(config.pubKey)
        val providerToBeEnabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_enable_provider", arrayOf(meProvider, providerToBeEnabled))
            sign(buildSigMaker())
        }
    }

    fun proposeEnableProvider(key: String) {
        sendTxSync(proposeEnableProviderInternal(key), "Enabling of provider has been proposed",
                "Cannot propose enabling of provider")
    }

    fun proposeBlockchain(blockchainConfigFile: String, nodes: String, format: String?, container: String) {
        sendTxSync(proposeBlockchainInternal(blockchainConfigFile, nodes, format, container), "Blockchain has been proposed",
                "Cannot add bc proposal")
    }

    /**
     * Propose add Blockchain to an existing container
     */
    fun proposeBlockchainInternal(blockchainConfigFile: String, nodes: String, format: String?, container: String) : GTXTransactionBuilder {
        val data = readConfigurationFile(blockchainConfigFile, format)
        return proposeBc(nodes, data, container)
    }

    fun proposeBlockchainGtvInternal(blockchainConfig: Gtv, nodes: String, container: String): GTXTransactionBuilder {
        val data = GtvEncoder.encodeGtv(blockchainConfig)
        return proposeBc(nodes, data, container)
    }

    private fun proposeBc(nodes: String, data: ByteArray, containerName: String): GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val container = containerGtv(containerName)
        val nodeList = nodes.split(",").map { nodeGtv(it) }
        return makeTransactionWithNop().apply {
            addOperation("propose_blockchain",
                    arrayOf(meProvider, GtvFactory.gtv(data), GtvFactory.gtv(nodeList), container))
            sign(buildSigMaker())
        }
    }

    fun proposeDisableProviderInternal(key: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val providerToBeDisabled = providerGtv(key)
        return makeTransactionWithNop().apply {
            addOperation("propose_disable_provider", arrayOf(meProvider, providerToBeDisabled))
            sign(buildSigMaker())
        }
    }

    fun proposeDisableProvider(key: String) {
        sendTxSync(proposeDisableProviderInternal(key), "Disabling of provider has been proposed",
                "Cannot propose disabling of provider")
    }

    fun proposeAddBlockchainSignersInternal(blockchainRID: String, signers: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val nodeList = signers.split(",").map { nodeGtv(it) }
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_add_blockchain_signers", arrayOf(meProvider, blockchain, GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }
    fun ProposeAddBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(proposeAddBlockchainSignersInternal(blockchainRID,signers),
                "proposal of new signers added successfully",
                "cannot add proposal of new signers for blockchain")
    }

    /** Who can stop a blokchain? Container configurator
     * */
    fun proposeStopBlockchainInternal(blockchainRID: String, removeReplicas: Boolean) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_stop_blockchain",
                    arrayOf(meProvider, blockchain, GtvFactory.gtv(removeReplicas)))
            sign(buildSigMaker())
        }
    }

    fun proposeStopBlockchain(blockchainRID: String, removeReplicas: Boolean) {
        sendTxSync(proposeStopBlockchainInternal(blockchainRID, removeReplicas),
                "blockchain stop proposition was added successfully",
                "Cannot add proposal for stopping blockchain")
    }

    fun proposeRemoveBlockchainSignersInternal(blockchainRID: String, signers: String) : GTXTransactionBuilder {
        val meProvider = providerGtv(config.pubKey)
        val nodeList = signers.split(",").map { nodeGtv(it) }
        val blockchain = blockchainGtv(blockchainRID)
        return makeTransactionWithNop().apply {
            addOperation("propose_remove_blockchain_signers",
                    arrayOf(meProvider, blockchain, GtvFactory.gtv(nodeList)))
            sign(buildSigMaker())
        }
    }
    fun proposeRemoveBlockchainSigners(blockchainRID: String, signers: String) {
        sendTxSync(proposeRemoveBlockchainSignersInternal(blockchainRID, signers),
                "Proposal on removing singers added",
                "Cannot ad proposal of removing signers from blockchain")
    }




    /**
     * This operation initializes the database with a first provider. If table `providers` is empty, the public key from
     * the module argument is registered as a first provider and enabled. Why? The system needs at least one provider,
     * that can vote for update proposals.
     */
    fun initInternal() : GTXTransactionBuilder {
        return makeTransactionWithNop().apply {
            addOperation("init", arrayOf<Gtv>())
            sign(buildSigMaker())
        }
    }
    fun init() {
        sendTxSync(initInternal(), "Initial provider added and enabled",
                "Cannot add and enable initial provider")
    }



    fun updateProvider(key: String, name: String) {
        sendTxSync(updateProviderInternal(key, name), "Provider data has been updated",
                "Cannot update provider")
    }

    fun updateProviderInternal(key: String, name: String): GTXTransactionBuilder {
        val provider = providerGtv(key)
        var data: Array<Gtv> = arrayOf(provider)
        if (name.isNotEmpty()) {
            data = data.plus(GtvFactory.gtv(name))
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().apply {
            addOperation("update_provider_data", data)
            sign(buildSigMaker())
        }
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

}