package net.postchain.mc.test

import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.enterprise0.CliExecutionE0
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Enterprise0Test : ManagedModeTest() {

    override fun chainConfSnippet(): String{
        val module = "enterprise0"

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
        return CliExecutionE0(cliConfig)
    }

    override val provExecutor = CliExecutionE0(provConfig)
    override val prov2Executor = CliExecutionE0(prov2Config)


    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @Before
    fun setup() {
        blockchain0ConfigGtv = run("enterprise0")
        doAndBuildBlocks(provConfig, provExecutor.initInternal())
    }

    @Test
    fun testProposeEnableDisableProvider() {

        //First provider adds node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

//        Then proposes a second provider to system cluster. Implies Also add it to system voter_set.
        addAndEnableSystemProv2()
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

    private fun addAndEnableSystemProv2() {
        doAndBuildBlocks(clientConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey, true, 1L, "system"))
        assertProviderData(prov2Config.pubKey, "", false)
        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderInternal(prov2Config.pubKey))
    }

    /**
     * Two active providers. Prov2 proposes disabling of Prov1. Prov1 votes no.
     */
    @Test
    fun testProposeProviderVoteNo() {

        addAndEnableSystemProv2()
        doAndBuildBlocks(clientConfig, prov2Executor.proposeDisableProviderInternal(provConfig.pubKey))

        voteNo("provider_state")
        assertEquals(2, provExecutor.listProviders().size, "")
    }

    @Test
    fun testProposeAddBlockchainXml() {

        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        //add new container to system cluster
        val container1 = "container1"
        doAndBuildBlocks(provConfig, provExecutor.createContainerInternal("system", container1))
//        val bcFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileName
        //propose new bc in new container:
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainInternal(bcConfig1xmlFile, nodes[0].pubKey, "xml", container1))
        assertBc0Added(clientConfig)
    }

    @Test
    fun testProposeConfigurationAcceptGtv() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, bcConfigGtvFile,
                20L, "gtv"))
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testProposeConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, bcConfig1xmlFile, 20L, "xml"))
        assertNextConfiguration(clientConfig, 20L)
    }

    private fun voteNo(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, false))
    }

    @Test(expected = org.awaitility.core.ConditionTimeoutException::class)
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        addAndEnableSystemProv2()

        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = "system")

        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + init + 3*addNode + proposeBlockhain + vote + addSigners + vote + 5 = 12
        // expected next congiguration height = -1 + init + 3*addNode + proposeBlockhain + addSigners + 5 = 10 (with vote included in proposal)
        assertNextConfiguration(clientConfig, 10L)

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
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        addAndEnableSystemProv2()
        // Add node1 to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, clusterName = "system")

        doAndBuildBlocks(prov2Config, provExecutor.removeNodeInternal(node1Pubkey))
        val listBlockchainSigners = provExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {
        val executor = cliExecution(clientConfig)
        initAndNode1Replica()

        doAndBuildBlocks(provConfig, provExecutor.proposeStopBlockchainInternal(clientConfig.brid, true))

        // query replicas again to ensure it was deleted after stop blockchain
        val replicas = executor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(0, replicas.size)

        // query signers again to ensure it was deleted after stop blockchain
        val listBlockchainSigners = executor.listBlockchainSigners(clientConfig.brid)
        assertEquals(0, listBlockchainSigners.size)
    }

    @Test
    fun testListBlockchainsForNode() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        var listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(0, listBlockchains.size)

        listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainLastHeight() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        val h = provExecutor.getBlockchainLastHeight(clientConfig.brid)
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 + vote = 3
        // expected height = -1 + init() + addNode0 + proposeBlockchain0 = 2 (vote included in proposal)
        assertEquals(2, h)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchain = provExecutor.getBlockchainConfiguration(clientConfig.brid, 0L)
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
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")
        var nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertTrue(nodeInfo["active"]!!.asBoolean())

        // Remove node1
        doAndBuildBlocks(clientConfig, provExecutor.removeNodeInternal(node1Pubkey))

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
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        val listBlockchains = provExecutor.listAllBlockchains()
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainReplicas() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addReplicaInternal(clientConfig.brid, node1Pubkey),5)
        assertBlockchainReplica(clientConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        //Add second system provider
        addAndEnableSystemProv2()

        // Prov2 adds new node to system cluster
        addNode(prov2Config, node1Pubkey, node1Host, node1Port, "system")

        val listBlockchainSigners = provExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testGetProposal() {
        addAndEnableSystemProv2()
        //Propose disabling of prov2 again
        doAndBuildBlocks(clientConfig, provExecutor.proposeDisableProviderInternal(prov2Config.pubKey))

        val type = "provider_state"
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
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainGtvInternal(blockchain0ConfigGtv, nodes[0].pubKey, container))
    }

    //First (the only) provider adds signers and vote yes to apply the change. This is not a general function.
    override fun addBcSigners(nodeList: String) {
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, nodeList))
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