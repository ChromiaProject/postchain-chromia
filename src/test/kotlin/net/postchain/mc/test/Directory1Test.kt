package net.postchain.mc.test

import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Directory1Test : ManagedModeTest() {

    override fun chainConfSnippet(): String{
        val module = "directory1"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="${module}">
                            <args module="${module}">
                                <arg key="initial_provider"><bytea>${KeyPairHelper.pubKeyHex(providerKey)}</bytea></arg>
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

    override fun cliExecution(cliConfig: ClientConfig): net.postchain.mc.cli.common0.CliExecution {
        return CliExecutionD1(cliConfig)
    }

    override val provExecutor = CliExecutionD1(provConfig)
    override val prov2Executor = CliExecutionD1(prov2Config)


    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @Before
    fun setup() {
        blockchain0ConfigGtv = run("directory1")
        doAndBuildBlocks(provConfig, provExecutor.initInternal())
    }

    /*
    @Test
    fun testProposeEnableDisableProvider() {

        //First provider adds node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

//        Then proposes a second provider to system cluster. Implies Also add it to system voter_set.
        addSystemProv2()
        assertProviderEnabled(prov2Config.pubKey)


        // The new provider adds node 1 to system cluster. It becomes automatically signer of bcs in cluster. TODO: Start as
        //  replica and once it is in sync make it signer, (to not cause a potential blockbuilding stop.)
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        //First provider proposes Disable prov2. Prov2 agrees:
        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderInternal(prov2Config.pubKey))
        val id = assertProposalTypeAndGetRowid("provider_state")
        doAndBuildBlocks(prov2Config, prov2Executor.voteInternal(id, true))
        assertProviderDisabled(prov2Config.pubKey)
    }

*/
    /**
     * Add provider prov2 as system provider. Includes activating it: active = true, system = true
     */
    private fun addSystemProv2() {
        doAndBuildBlocks(provConfig, provExecutor.registerProviderInternal(prov2Config.pubKey, 1L))
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemInternal(prov2Config.pubKey, true))
        assertProviderData(prov2Config.pubKey, "", true)
    }

    /**
     * Two active providers. Prov2 proposes demotion of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeDegradeProviderVoteNo() {

        addSystemProv2()
        // Prov2 proposes degradation/demotion of prov1.
        doAndBuildBlocks(provConfig, prov2Executor.proposeProviderIsSystemInternal(provConfig.pubKey, false))

        // Prov votes no
        voteNo("provider_is_system")
        val listVoterset = provExecutor.listVoterSetMembers(voterSetSystemP)
        assertEquals(2, listVoterset.size, "")
    }

    /**
     * New voter set with two providers and SYSTEM_P as governor
     */
    @Test
    fun testCreateVoterSet() {

        addSystemProv2()

        val voterSetName = "Ellen"
        val providers_list = "${provConfig.pubKey},${prov2Config.pubKey}"
        doAndBuildBlocks(provConfig, provExecutor.createVoterSetInternal(voterSetName, providers_list, 0,
                voterSetSystemP))
        assertAdded("get_voter_set", "name", GtvString(voterSetName))
    }

    @Test
    fun testProposeAddBlockchainXml() {

        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerInternal("system", container1))
        //propose new bc in new container:
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainInternal(bcConfig1xmlFile, "xml", container1))
        assertEquals(2, provExecutor.listAllBlockchains().size)
    }

    @Test
    fun testCreateCluster() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val addedItem = "Vera"
        val providers_list = provConfig.pubKey
//        val providers_list = "${provConfig.pubKey},${provConfig.pubKey}"
        //create cluster, initial providers added
        doAndBuildBlocks(provConfig, provExecutor.createClusterInternal(addedItem, providers_list,
                voterSetSystemP, voterSetSystemP))

        assertAdded("get_cluster", "name", GtvString(addedItem))
    }



    @Test
    fun testProposeConfigurationAcceptGtv() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(provConfig.brid, bcConfigGtvFile,
                20L, "gtv"))
        assertNextConfiguration(provConfig, 20L)
    }

    @Test
    fun testProposeConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(provConfig.brid, bcConfig1xmlFile,
                20L, "xml"))
        assertNextConfiguration(provConfig, 20L)
    }

    private fun voteNo(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, false))
    }

    @Test(expected = org.awaitility.core.ConditionTimeoutException::class)
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        addSystemProv2()

        //prov adds prov2 to system cluster
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterInternal(prov2Config.pubKey, "system"))

        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = "system")

        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + init + 3*addNode + proposeBlockhain + addprov2toCluster + addNode + 5 = 11 (with vote included in proposal)
        assertNextConfiguration(provConfig, 11L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(2)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        Awaitility.await().atMost(Duration.ONE_SECOND).until {
            doAndBuildBlocks(provConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey, false, 1, "system"))
            true
        }
    }

    @Test
    fun testRemoveBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        addSystemProv2()
        // Prov2 adds node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = "system")

        doAndBuildBlocks(prov2Config, provExecutor.removeNodeInternal(node1Pubkey))
        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {
        val executor = cliExecution(provConfig)
        initAndNode1Replica()

        doAndBuildBlocks(provConfig, provExecutor.proposeStopBlockchainInternal(provConfig.brid, true))

        // query replicas again to ensure it was deleted after stop blockchain
        val replicas = executor.listBlockchainReplicas(provConfig.brid)
        assertEquals(0, replicas.size)

        // query signers again to ensure it was deleted after stop blockchain
        val listBlockchainSigners = executor.listBlockchainSigners(provConfig.brid)
        assertEquals(0, listBlockchainSigners.size)
    }

    @Test
    fun testListBlockchainsForNode() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        var listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(0, listBlockchains.size)

        listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainLastHeight() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val h = provExecutor.getBlockchainLastHeight(provConfig.brid)
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 + vote = 3
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 = 2 (vote included in proposal)
        assertEquals(2, h)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val blockchain = provExecutor.getBlockchainConfiguration(provConfig.brid, 0L)
        assert(blockchain.isNotEmpty())
        val modules = GtvFactory.decodeGtv(blockchain).asDict()["gtx"]?.get("modules")
        assertEquals("net.postchain.rell.module.RellPostchainModuleFactory", modules?.get(0)?.asString())
    }

    @Test
    fun testGetNodeListVersion() {
        addNode0(provConfig, "")
        val version = provExecutor.getNodeListVersion()
        assertTrue(version > 0)
    }

    @Test
    fun testRemoveNode() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")
        var nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertTrue(nodeInfo["active"]!!.asBoolean())

        // Remove node1
        doAndBuildBlocks(provConfig, provExecutor.removeNodeInternal(node1Pubkey))

        nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertFalse(nodeInfo["active"]!!.asBoolean())
    }

    @Test
    fun testListNodes() {
        addNode0(provConfig, "")
        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")
        assertListNodes()

    }

    @Test
    fun testListNodesWithProvider() {
        addNode0(provConfig, "")
        assertListNodesNode0(provConfig)
    }

    @Test
    fun testListBlockchains() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        val listBlockchains = provExecutor.listAllBlockchains()
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainReplicas() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addReplicaInternal(provConfig.brid, node1Pubkey),5)
        assertBlockchainReplica(provConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //Add second system provider
        addSystemProv2()

        //prov adds prov2 to system cluster
        doAndBuildBlocks(provConfig, provExecutor.addProviderToClusterInternal(prov2Config.pubKey, "system"))

        // Prov2 adds new node to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provExecutor.listBlockchainSigners(provConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testGetProposal() {
        addSystemProv2()
        //Propose degradation of prov2 again
        doAndBuildBlocks(provConfig, provExecutor.proposeProviderIsSystemInternal(prov2Config.pubKey, false))

        val type = "provider_is_system"
        val id = assertProposalTypeAndGetRowid(type)
        val proposal = provExecutor.getProposal(id).asDict()
        val actualType = (proposal["proposal_type"] as GtvString).string
        val propid = (proposal["rowid"] as GtvInteger).asInteger()
        val timestamp = (proposal["timestamp"] as GtvInteger).asInteger()
        val proposedBy = proposal["proposed_by"]!!.asByteArray().toHex()

        assertEquals(provConfig.pubKey, proposedBy, "wrong proposed_by")
        assertEquals(id, propid, "wrong idx")
        assertEquals(type, actualType, "Wrong proposal type")
        assertNotEquals(0, timestamp, "timestamp is 0")

    }

    override fun addBc(blockchain0ConfigGtv: Gtv, container: String) {
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainGtvInternal(blockchain0ConfigGtv, container))
    }

    //First (the only) provider adds signers and vote yes to apply the change. This is not a general function.
    override fun addBcSigners(nodeList: String) {
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(provConfig.brid, nodeList))
    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: String): Long {
        val proposals = provExecutor.listProposalsSince(0)
        val type = (proposals[0].asDict()["proposal_type"] as GtvString).string
        val id = (proposals[0].asDict()["rowid"] as GtvInteger).asInteger()
        assertEquals(expectedType, type, "Wrong proposal type")
        return id
    }
}