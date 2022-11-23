package net.postchain.mc.test

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.chain0.common.addBlockchainReplicaOperation
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.getProposal
import net.postchain.chain0.common.proposal.proposeBlockchainActionOperation
import net.postchain.chain0.common.queries.getBlockchainReplicas
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getClusterNodes
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.chain0.common.removeNodeOperation
import net.postchain.chain0.model.BlockchainAction
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.crypto.PubKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.common0.CliExecution
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Directory1IT : ManagedModeTest() {

    override fun chainConfSnippet(): String {
        val module = "directory1"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="$module">
                            <args module="common.init">
                                <arg key="initial_provider"><bytea>${KeyPairHelper.pubKeyHex(providerKey)}</bytea></arg>
                                <arg key="genesis_node">
                                    <array>
                                        <bytea>${KeyPairHelper.pubKeyHex(node0BlockSignerKey)}</bytea>
                                        <string>${node0Host}</string>
                                        <int>${node0Port}</int>
                                        <string>{apiUrl}</string>
                                    </array>
                                </arg>
                            </args>
                        </app>
                        <gtv path="signers">
                            <array>
                                <bytea>${KeyPairHelper.pubKeyHex(node0BlockSignerKey)}</bytea>
                            </array>
                        </gtv>
                    </config>
                </chain>
            </chains>
        """.trimIndent()
    }

    override fun cliExecution(cliConfig: PostchainClientConfig): CliExecution {
        return CliExecution(cliConfig)
    }

    override val provExecutor by lazy { CliExecution(provConfig) }
    override val prov2Executor by lazy { CliExecution(prov2Config) }

    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @BeforeEach
    fun setup() {
        blockchain0ConfigGtv = run(runXmlFile(), File("../../chain0-impl/rell/src"))
        doAndBuildBlocks(provConfig, provExecutor.getPostchainClient().transactionBuilder().initOperation(null))
    }

    @Test
    fun testProposeEnableDisableProvider() {
        //Then proposes a second provider to system cluster. Includes also add it to system voter_set.
        addSystemProv2()
        assertProviderEnabled(prov2Config.pubkey())

        // The new provider adds node 1 to system cluster. It becomes automatically signer of bcs in cluster. TODO: Start as
        //  replica and once it is in sync make it signer, (to not cause a potential blockbuilding stop.)
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, systemClusterName)

        //First provider proposes Disable prov2. Prov2 agrees:
        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubkey()))
        val id = assertProposalTypeAndGetRowid(ProposalType.provider_state).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        assertProviderDisabled(prov2Config.pubkey())
    }

    /**
     * Add provider prov2 as system provider. Includes proposeEnable and promoting to system: active = true, system = true
     */
    private fun addSystemProv2() {
        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubkey(), true))
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubkey(), true))
        assertProviderData(prov2Config.pubkey(), "", true)
    }

    /**
     * Two active providers. Prov2 proposes demotion of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeDegradeProviderVoteNo() {
        addSystemProv2()
        // Prov2 proposes degradation/demotion of prov1.
        doAndBuildBlocks(provConfig, prov2Executor.proposeProviderIsSystemAsync(provConfig.pubkey(), false))

        // Prov votes no
        voteNo(ProposalType.provider_is_system)
        val listVoterSet = provExecutor.listVoterSetMembers(voterSetSystemP)
        assertEquals(2, listVoterSet.size)
    }

    /**
     * New voter set with two providers and SYSTEM_P as governor. Create and update members. Change governor.
     */
    @Test
    fun testVoterSet() {
        addSystemProv2()

        val voterSetName = "Ellen"
        val providersList = "${provConfig.pubkey()},${prov2Config.pubkey()}"
        doAndBuildBlocks(
                provConfig, provExecutor.createVoterSetAsync(
                voterSetName, providersList, 0,
                voterSetSystemP
        )
        )
        assertAdded("get_voter_set", "name", GtvString(voterSetName))
        val listVotersets = provExecutor.listVoterSets()
        assertEquals(voterSetName, listVotersets[2].name)
        assertEquals(voterSetSystemP, provExecutor.getVoterSetGovernor(voterSetName))

        var members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey(), prov2Config.pubkey()), members.map { it.toHex() })


        //remove prov2 from Ellen. Note that with two providers in governance set, both must be OK with the member update.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubkey(), false))
        var id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey()), members.map { it.toHex() })

        //Make Ellen her own governor.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetGovernorAsync(voterSetName, voterSetName))
        id = assertProposalTypeAndGetRowid(ProposalType.voter_set_update).id
        doAndBuildBlocks(prov2Config, prov2Executor.voteAsync(id, true))

        //add prov2 to voter set Ellen again. Since now only one member, no voting is needed for this proposal to be applied.
        doAndBuildBlocks(provConfig, provExecutor.proposeVoterSetMemberAsync(voterSetName, prov2Config.pubkey(), true))
        members = provExecutor.listVoterSetMembers(voterSetName)
        assertEquals(listOf(provConfig.pubkey(), prov2Config.pubkey()), members.map { it.toHex() })
    }

    @Test
    fun testProposeAddBlockchainXmlWithDependency() {
        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        //propose new bc in new container:
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))
        assertEquals(2, provExecutor.listBlockchains(false).size)

        //test building blocks for new bc
        buildBlock(100, 4)

        //add yet another bc, dependent on previous one
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlDependencyFile, "xml", container1, "2"))
        val listOfBcs = provExecutor.getPostchainClient().getBlockchains(false)
        val bc1 = listOfBcs.find { it.name == "1" }!!
        val bc2 = listOfBcs.find { it.name == "2" }!!

        val listOfDependencies = provExecutor.listBlockchainDependencies(bc2.rid.toHex(), 0)
        assertEquals(1, listOfDependencies.size)
        assertEquals(bc1.rid.toHex(), listOfDependencies[0].first.toHex())
        assertEquals(container1, listOfDependencies[0].second)

        //Now make sure that you cannot delete a bc that someone else is dependent on
        proposeBlockchainAction(provClient, bc1.rid.data, BlockchainAction.remove)
        assertEquals(3, provExecutor.listBlockchains(false).size)
    }

    /**
     * Includes creating, listing of clusters as well as updating the cluster providers. And updating deployer.
     */
    @Test
    fun testCluster() {
        val newClusterName = "Vera"
        val providersList = provConfig.pubkey()
        //create cluster, initial providers added
        doAndBuildBlocks(
                provConfig, provExecutor.createClusterAsync(
                newClusterName, providersList,
                voterSetSystemP
        )
        )
        assertAdded("get_cluster", "name", GtvString(newClusterName))

        var clusters = provExecutor.listClustersForProvider(provConfig.pubkey())
        assertEquals(listOf(systemClusterName, newClusterName), clusters)

        doAndBuildBlocks(provConfig, provExecutor.registerProviderAsync(prov2Config.pubkey(), true))
        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderAsync(prov2Config.pubkey()))
        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubkey(), add = true)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubkey())
        assertEquals(listOf(newClusterName), clusters)

        doAndBuildBlocks(
                provConfig,
                provExecutor.proposeClusterProviderAsync(newClusterName, prov2Config.pubkey(), add = false)
        )
        clusters = provExecutor.listClustersForProvider(prov2Config.pubkey())
        assertEquals(listOf(), clusters)

        // cluster providers
        val clusterProviders = provExecutor.getClusterProviders(newClusterName)
        println(clusterProviders.toTypedArray().contentToString())
        assert(clusterProviders.size).isEqualTo(1)
        assert(clusterProviders.first().pubkey.hex()).isEqualTo(provConfig.pubkey())
        // UNKNOWN cluster providers
        assertThrows<UserMistake> {
            provExecutor.getClusterProviders("unknown cluster name")
        }
    }

    @Test
    fun testProposeConfiguration() {
        doAndBuildBlocks(
                provConfig, provExecutor.proposeConfigurationAsync(
                provConfig.blockchainRid.toHex(), bcConfig1xmlFile,
                20L, "xml", false
        )
        )
        assertNextConfiguration(provConfig, 20L)
    }

    private fun voteNo(proposalType: ProposalType) {
        val id = assertProposalTypeAndGetRowid(proposalType).id
        doAndBuildBlocks(provConfig, provExecutor.voteAsync(id, false))
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        //includes prov adds prov2 to system cluster
        addSystemProv2()

        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)

        // Get next configuration height after adding new node as blockchain's signer
        // expected next configuration height = -1 + init + addNode + proposeSystemProvider + proposeBlockhain + addNode + 4 = 10 (with vote included in proposal)
        assertNextConfiguration(provConfig, 8L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(2)

        // Try to send tnx to api end point after the blockchain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderAsync(prov2Config.pubkey()))
                true
            }
        }
    }

    @Test
    fun testRemoveBlockchainSigners() {
        addSystemProv2()
        // Prov2 adds node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = systemClusterName)
        assertEquals(2, provExecutor.getPostchainClient().getBlockchainSigners(provConfig.blockchainRid).size)

        val tx = prov2Executor.getPostchainClient().transactionBuilder()
                .removeNodeOperation(
                        prov2Config.signers.first().pubKey.data,
                        node1Pubkey.hexStringToByteArray()
                )
        doAndBuildBlocks(prov2Config, tx)
        assertEquals(1, provExecutor.getPostchainClient().getBlockchainSigners(provConfig.blockchainRid).size)
    }

    @Test
    fun testPauseBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))

        //pause new bc
        val listOfBcs = provExecutor.getPostchainClient().getBlockchains(false)
        val bridToPause = listOfBcs.find { it.name == "1" }!!.rid.data
        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.pause)

        var bcs = provExecutor.listBlockchains(true)
        assertEquals(2, bcs.size)
        bcs = provExecutor.listBlockchains(false)
        assertEquals(1, bcs.size)

        proposeBlockchainAction(provClient, bridToPause, BlockchainAction.resume)
        bcs = provExecutor.listBlockchains(false)
        assertEquals(2, bcs.size)

        // build after unpause
        buildBlock(100, 3)
    }

    @Test
    fun testDeleteBlockchain() {
        //add new bc in new container in system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerAsync(container1, systemClusterName, voterSetSystemP))
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainAsync(bcConfig1xmlFile, "xml", container1, "1"))

        val listOfBcs = provExecutor.getPostchainClient().getBlockchains(false)
        val bc1 = listOfBcs.find { it.name == "1" }!!.rid.data
        assertEquals(2, listOfBcs.size)

        // delete new bc
        proposeBlockchainAction(provClient, bc1, BlockchainAction.remove)

        val bcs = provExecutor.listBlockchains(true)
        assertEquals(1, bcs.size)
    }

    @Test
    fun testListBlockchainsForNode() {
        var listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(1, listBlockchains.size) // Always know about itself

        listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainLastHeight() {
        val h = provExecutor.getBlockchainLastHeight(provConfig.blockchainRid.toHex())
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 + vote = 3
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 = 2 (vote included in proposal)
        assertEquals(0, h)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        val blockchain = provExecutor.getBlockchainConfiguration(provConfig.blockchainRid.toHex(), 0L)
        assert(blockchain.isNotEmpty())
        val modules = GtvFactory.decodeGtv(blockchain).asDict()["gtx"]?.get("modules")
        assertEquals("net.postchain.rell.module.RellPostchainModuleFactory", modules?.get(0)?.asString())
    }

    @Test
    fun testGetNodeListVersion() {
        addNode0(provConfig, "")
        val version = provExecutor.getPeerListVersion()
        assertTrue(version > 0)
    }

    @Test
    fun testListNodesWithProvider() {
        addNode0(provConfig, "")

        val providerNodes = provExecutor.listNodesByProvider(provConfig.pubkey())
        assertEquals(1, providerNodes.size)

        val nodeList = provExecutor.listNodesWithProvider()
        assertNodeInfo(nodeList[0], node0Host, node0Port, nodes[0].pubKey, provConfig.pubkey(), true)
    }

    @Test
    fun testListBlockchains() {
        val listBlockchains = provExecutor.listBlockchains(false)
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainSigners() {
        //Add second system provider
        addSystemProv2()

        // Prov2 adds new node to system cluster => Two blockchain signers in system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provExecutor.getPostchainClient().getBlockchainSigners(provConfig.blockchainRid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testGetProposal() {
        addSystemProv2()
        //Propose degradation of prov2 again
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemAsync(prov2Config.pubkey(), false))

        val type = ProposalType.provider_is_system
        val id = assertProposalTypeAndGetRowid(type)
        val proposal = provExecutor.getPostchainClient().getProposal(id)!!
        val actualType = proposal.type
        val propid = proposal.id
        val timestamp = proposal.timestamp
        val proposedBy = proposal.proposedBy.toHex()

        assertEquals(provConfig.pubkey(), proposedBy, "wrong proposed_by")
        assertEquals(id, propid, "wrong idx")
        assertEquals(ProposalType.provider_is_system, actualType, "Wrong proposal type")
        assertNotEquals(0, timestamp, "timestamp is 0")
    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: ProposalType): RowId {
        val proposals = provExecutor.listProposalsSince(0)
        val type = (proposals[0].proposalType.name)
        assertEquals(expectedType.toString(), type, "Wrong proposal type")
        return (proposals[0].rowid)
    }

    private fun proposeBlockchainAction(provClient: PostchainClient, brid: ByteArray, action: BlockchainAction) {
        provClient.transactionBuilder().proposeBlockchainActionOperation(
                provClient.config.signers.first().pubKey.data, BlockchainRid(brid), action
        ).also {
            doAndBuildBlocks(provClient.config, it)
        }
    }
}