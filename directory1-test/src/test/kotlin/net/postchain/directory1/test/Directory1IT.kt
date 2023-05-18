package net.postchain.directory1.test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import mu.KLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.queries.getBlockchain
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.chain0.common.queries.getNodesWithProvider
import net.postchain.chain0.common.queries.getProviderClusters
import net.postchain.chain0.common.queries.getVoterSetGovernor
import net.postchain.chain0.common.queries.getVoterSetMembers
import net.postchain.chain0.common.queries.getVoterSets
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerFromOperation
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmComputeBlockchainList
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetBlockchainDependencies
import net.postchain.chain0.proposal.ProposalType
import net.postchain.chain0.proposal.getProposal
import net.postchain.chain0.proposal.getProposalsSince
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal.voting.makeVoteOperation
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationAtOperation
import net.postchain.chain0.proposal_cluster.proposeClusterProviderOperation
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.chain0.proposal_provider.proposeProviderStateOperation
import net.postchain.chain0.proposal_voter_set.proposeUpdateVoterSetOperation
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtvml.GtvMLParser
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class Directory1IT : ManagedModeTest() {

    companion object : KLogging()

    /**
     * The pre-step includes starting a single node, running the Rell code.
     *  Operation init() registers and enables the module argument
     * `initial_provider` as provider. So that we have an initial voter.
     */
    @BeforeEach
    fun setup() {
        run(GtvMLParser.parseGtvML(this::class.java.getResource("/directory1/manager.xml")!!.readText()))
        doAndBuildBlocks(provClient.transactionBuilder().initOperation(null, null))
    }

    @Test
    fun testProposeEnableDisableProvider() {
        addSystemProv2()
        assertProviderEnabled(prov2Config.pubkey())

        logger.info("First provider proposes Disable prov2. Prov2 agrees.")
        val tx = provClient.transactionBuilder().addNop()
                .proposeProviderStateOperation(pubKeyOf(provConfig), pubKeyOf(prov2Config), false, "")
        doAndBuildBlocks(tx)
        logger.info("assertProposalTypeAndGetRowid")
        val id = assertProposalTypeAndGetRowid(ProposalType.provider_state).id
        val tx2 = prov2Client.transactionBuilder().addNop().makeVoteOperation(pubKeyOf(prov2Client.config), id, true)
        doAndBuildBlocks(tx2)
        logger.info("assertProviderDisabled")
        assertProviderDisabled(prov2Config.pubkey())
    }

    /**
     * Add provider prov2 as system provider. Includes proposeEnable and promoting to system: active = true, system = true
     */
    private fun addSystemProv2() {
        logger.info("proposes a second provider to system cluster. Includes also add it to system voter_set.")
        doAndBuildBlocks(registerProvider(provClient, prov2Config.pubkey(), true))
        doAndBuildBlocks(proposeProviderIsSystem(provClient, prov2Config.pubkey(), true))
        assertProviderData(prov2Config.pubkey(), "", true)
    }

    /**
     * Two active providers. Prov2 proposes demotion of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeDegradeProviderVoteNo() {
        addSystemProv2()
        // Prov2 proposes degradation/demotion of prov1.
        doAndBuildBlocks(proposeProviderIsSystem(prov2Client, provConfig.pubkey(), false))

        // Prov votes no
        val id = assertProposalTypeAndGetRowid(ProposalType.provider_is_system).id
        val tx = provClient.transactionBuilder().addNop().makeVoteOperation(pubKeyOf(provClient.config), id, false)
        doAndBuildBlocks(tx)

        val listVoterSet = provClient.getVoterSetMembers(voterSetSystemP)
        assertEquals(2, listVoterSet.size)
    }

    /**
     * New voter set with two providers and SYSTEM_P as governor. Create and update members. Change governor.
     */
    @Test
    fun testVoterSet() {
        addSystemProv2()

        val voterSetName = "Ellen"
        val tx = provClient.transactionBuilder().addNop().createVoterSetOperation(
                pubKeyOf(provClient.config), voterSetName, 0, listOf(pubKeyOf(provConfig), pubKeyOf(prov2Config)), voterSetSystemP
        )
        doAndBuildBlocks(tx)
        assertAdded("get_voter_set", "name", GtvString(voterSetName))
        val listVotersets = provClient.getVoterSets()
        assertEquals(voterSetName, listVotersets[2].name)
        assertEquals(voterSetSystemP, provClient.getVoterSetGovernor(voterSetName))

        var members = provClient.getVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey().hex(), prov2Config.pubkey().hex()), members.map { it.toHex() })

        //remove prov2 from Ellen. Note that with two providers in governance set, both must be OK with the member update.
        val tx1 = provClient.transactionBuilder().addNop()
                .proposeUpdateVoterSetOperation(
                        pubKeyOf(provClient.config),
                        voterSetName,
                        null,
                        null,
                        listOf(),
                        listOf(pubKeyOf(prov2Config)),
                        ""
                )
        doAndBuildBlocks(tx1)
        var id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        val tx0 = prov2Client.transactionBuilder().addNop().makeVoteOperation(pubKeyOf(prov2Client.config), id, true)
        doAndBuildBlocks(tx0)
        members = provClient.getVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey().hex()), members.map { it.toHex() })

        //Make Ellen her own governor.
        val tx2 = provClient.transactionBuilder().addNop().proposeUpdateVoterSetOperation(
                pubKeyOf(provClient.config), voterSetName, null, voterSetName, listOf(), listOf(), ""
        )
        doAndBuildBlocks(tx2)
        id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        val tx3 = prov2Client.transactionBuilder().addNop().makeVoteOperation(pubKeyOf(prov2Client.config), id, true)
        doAndBuildBlocks(tx3)

        //add prov2 to voter set Ellen again. Since now only one member, no voting is needed for this proposal to be applied.
        val tx4 = provClient.transactionBuilder().addNop()
                .proposeUpdateVoterSetOperation(
                        pubKeyOf(provClient.config),
                        voterSetName,
                        null,
                        null,
                        listOf(pubKeyOf(prov2Config)),
                        listOf(),
                        ""
                )
        doAndBuildBlocks(tx4)
        members = provClient.getVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey().hex(), prov2Config.pubkey().hex()), members.map { it.toHex() })
    }

    @Test
    fun testProposeAddBlockchainXmlWithDependency() {
        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(createContainer(provClient, container1, systemClusterName, voterSetSystemP))
        //propose new bc in new container:
        doAndBuildBlocks(proposeBlockchain(provClient, bcConfig1xmlFile, container1, "1"))
        assertEquals(2, listBlockchains(false).size)

        //test building blocks for new bc
        buildBlock(100, 4)

        //add yet another bc, dependent on previous one
        doAndBuildBlocks(proposeBlockchain(provClient, bcConfig1xmlDependencyFile, container1, "2"))
        val listOfBcs = provClient.getBlockchains(false)
        val bc1 = listOfBcs.find { it.name == "1" }!!
        val bc2 = listOfBcs.find { it.name == "2" }!!

        val listOfDependencies = listBlockchainDependencies(bc2.rid, 0)
        assertEquals(1, listOfDependencies.size)
        assertEquals(bc1.rid.toHex(), listOfDependencies[0].first.toHex())
        assertEquals(container1, listOfDependencies[0].second)

        //Now make sure that you cannot delete a bc that someone else is dependent on
        proposeBlockchainAction(provClient, bc1.rid.data, BlockchainAction.remove)
        assertEquals(3, listBlockchains(false).size)
    }

    /**
     * Includes creating, listing of clusters as well as updating the cluster providers. And updating deployer.
     */
    @Test
    fun testCluster() {
        val newClusterName = "Vera"

        val tx = provClient.transactionBuilder().addNop()
                .createClusterOperation(
                        pubKeyOf(provClient.config),
                        newClusterName,
                        voterSetSystemP,
                        listOf(pubKeyOf(provConfig))
                )
        doAndBuildBlocks(tx)
        assertAdded("get_cluster", "name", GtvString(newClusterName))

        var clusters = provClient.getProviderClusters(provConfig.pubkey())
        assertEquals(listOf(systemClusterName, newClusterName), clusters)

        doAndBuildBlocks(registerProvider(provClient, prov2Config.pubkey(), true))

        val tx2 = provClient.transactionBuilder().addNop()
                .proposeProviderStateOperation(pubKeyOf(provConfig), pubKeyOf(prov2Config), true, "")
        doAndBuildBlocks(tx2)

        val tx3 = provClient.transactionBuilder().addNop().proposeClusterProviderOperation(
                pubKeyOf(provClient.config), newClusterName, prov2Config.pubkey().data, true, ""
        )
        doAndBuildBlocks(tx3)
        clusters = provClient.getProviderClusters(prov2Config.pubkey())
        assertEquals(listOf(newClusterName), clusters)

        val tx4 = provClient.transactionBuilder().addNop().proposeClusterProviderOperation(
                pubKeyOf(provClient.config), newClusterName, prov2Config.pubkey().data, false, ""
        )
        doAndBuildBlocks(tx4)
        clusters = provClient.getProviderClusters(prov2Config.pubkey())
        assertThat(clusters).isEmpty()

        // cluster providers
        val clusterProviders = provClient.getClusterProviders(newClusterName)
        println(clusterProviders.toTypedArray().contentToString())
        assertThat(clusterProviders.size).isEqualTo(1)
        assertThat(clusterProviders.first().pubkey).isEqualTo(provConfig.pubkey().wData)
        // UNKNOWN cluster providers
        assertThrows<UserMistake> {
            provClient.getClusterProviders("unknown cluster name")
        }
    }

    @Test
    fun testProposeConfigurationAt() {
        proposeConfigAt(1000, 10, false)
        assertNextConfiguration(provConfig, 10, 1000)

        proposeConfigAt(1001, 8, false)
        assertNextConfiguration(provConfig, 8, 1001)
    }

    private fun proposeConfigAt(configId: Int, height: Long, force: Boolean) {
        val configFile = getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_$configId.xml")
        val configData = BlockchainConfig.readFromFile(configFile).data
        provClient.transactionBuilder().addNop()
                .proposeConfigurationAtOperation(
                        pubKeyOf(provConfig), provConfig.blockchainRid, configData, height, force, ""
                ).post()
        buildAndAwaitBlocks(1, false)
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        //includes prov adds prov2 to system cluster
        addSystemProv2()

        // Add node1 to system cluster
        addNode(prov2Client, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        // Try to send tnx to api end point after the blockchain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                val tx = provClient.transactionBuilder().addNop()
                        .proposeProviderStateOperation(pubKeyOf(provConfig), pubKeyOf(prov2Config), false, "")
                doAndBuildBlocks(tx)
                true
            }
        }
    }

    @Test
    fun testPauseBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(createContainer(provClient, container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(proposeBlockchain(provClient, bcConfig1xmlFile, container1, "1"))

        //pause new bc
        val listOfBcs = provClient.getBlockchains(false)
        val bridToPause = listOfBcs.find { it.name == "1" }!!.rid.data
        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.pause)
        assertEquals(2, listBlockchains(true).size)
        assertEquals(1, listBlockchains(false).size)

        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.resume)
        assertEquals(2, listBlockchains(false).size)

        // build after unpause
        buildBlock(100, 3)
    }

    @Test
    fun testDeleteBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(createContainer(provClient, container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(proposeBlockchain(provClient, bcConfig1xmlFile, container1, "1"))

        val listOfBcs = provClient.getBlockchains(false)
        val bc1 = listOfBcs.find { it.name == "1" }!!.rid.data
        assertEquals(2, listOfBcs.size)

        // delete new bc
        proposeBlockchainAction(provClient, bc1, BlockchainAction.remove)
        assertEquals(1, listBlockchains(true).size)
    }

    @Test
    fun testListBlockchainsForNode() {
        var listBlockchains = provClient.nmComputeBlockchainList(node1Pubkey.hexStringToByteArray())
        assertEquals(1, listBlockchains.size) // Always know about itself

        listBlockchains = provClient.nmComputeBlockchainList(nodes[0].pubKey.hexStringToByteArray())
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        val blockchain = requireNotNull(provClient.nmGetBlockchainConfiguration(provConfig.blockchainRid, 0L))
        assertThat(blockchain).isNotEmpty()
        val modules = GtvFactory.decodeGtv(blockchain).asDict()["gtx"]?.get("modules")
        assertEquals("net.postchain.rell.module.RellPostchainModuleFactory", modules?.get(0)?.asString())
    }

    @Test
    fun testListNodesWithProvider() {
        val providerNodes = provClient.getNodesByProvider(provConfig.signers.first().pubKey)
        assertEquals(1, providerNodes.size)

        val nodeList = provClient.getNodesWithProvider()
        assertNodeInfo(nodeList[0], node0Host, node0Port, PubKey(nodes[0].pubKey), provConfig.pubkey(), true)
    }

    @Test
    fun testListBlockchains() {
        assertEquals(1, listBlockchains(false).size)
    }

    @Test
    fun testListBlockchainSigners() {
        //Add second system provider
        addSystemProv2()

        // Prov2 adds new node to system cluster => Two blockchain signers in system cluster
        addNode(prov2Client, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provClient.getBlockchainSigners(provConfig.blockchainRid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testGetProposal() {
        addSystemProv2()
        //Propose degradation of prov2 again
        doAndBuildBlocks(proposeProviderIsSystem(provClient, prov2Config.pubkey(), false))

        val type = ProposalType.provider_is_system
        val id = assertProposalTypeAndGetRowid(type)
        val proposal = provClient.getProposal(id)!!
        val actualType = proposal.type
        val propid = proposal.id
        val timestamp = proposal.timestamp
        val proposedBy = proposal.proposedBy

        assertEquals(provConfig.pubkey().wData, proposedBy, "wrong proposed_by")
        assertEquals(id, propid, "wrong idx")
        assertEquals(ProposalType.provider_is_system, actualType, "Wrong proposal type")
        assertNotEquals(0, timestamp, "timestamp is 0")
    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: ProposalType): RowId {
        val proposals = provClient.getProposalsSince(RowId(0))
        val type = (proposals[0].proposalType.name)
        assertEquals(expectedType.toString(), type, "Wrong proposal type")
        return (proposals[0].rowid)
    }

    private fun proposeBlockchainAction(provClient: PostchainClient, brid: ByteArray, action: BlockchainAction) {
        provClient.transactionBuilder().proposeBlockchainActionOperation(
                pubKeyOf(provClient.config), BlockchainRid(brid), action, ""
        ).also {
            doAndBuildBlocks(it)
        }
    }

    private fun pubKeyOf(clientConfig: PostchainClientConfig) = clientConfig.signers.first().pubKey.data

    private fun listBlockchains(includeInactive: Boolean): List<ByteArray> {
        return provClient.getBlockchains(includeInactive).map { it.rid.data }
    }

    private fun listBlockchainDependencies(blockchainRID: WrappedByteArray, height: Long): List<Pair<ByteArray, String>> {
        val listBlockChainContainerPair = arrayListOf<Pair<ByteArray, String>>()
        try {
            val blockchainGtv = provClient.getBlockchain(blockchainRID.data)
            val list = provClient.nmGetBlockchainDependencies(blockchainGtv, height)
            list.forEach {
                val rid = it[0].asByteArray()
                val container = it[1].asString()
                listBlockChainContainerPair.add(rid to container)
            }
        } catch (e: Exception) {
            logger.error { e.message }
        }
        return listBlockChainContainerPair
    }

    private fun registerProvider(client: PostchainClient, key: PubKey, nodeProvider: Boolean): TransactionBuilder {
        return client.transactionBuilder().addNop().registerProviderOperation(
                pubKeyOf(client.config),
                key,
                if (nodeProvider) ProviderTier.NODE_PROVIDER else ProviderTier.COMMUNITY_NODE_PROVIDER
        )
    }

    private fun createContainer(client: PostchainClient, containerName: String, clusterName: String, deployerName: String): TransactionBuilder {
        return client.transactionBuilder().addNop().createContainerFromOperation(
                pubKeyOf(client.config), containerName, clusterName, 1, deployerName)
    }

    private fun proposeProviderIsSystem(client: PostchainClient, pubKey: PubKey, isSystem: Boolean): TransactionBuilder {
        return client.transactionBuilder().addNop().proposeProviderIsSystemOperation(
                pubKeyOf(client.config), pubKey.data, isSystem, ""
        )
    }

    private fun proposeBlockchain(client: PostchainClient, blockchainConfigFile: File, container: String, name: String): TransactionBuilder {
        val data = BlockchainConfig.readFromFile(blockchainConfigFile).data
        return client.transactionBuilder().addNop().proposeBlockchainOperation(
                pubKeyOf(client.config), data, name, container, ""
        )
    }
}
