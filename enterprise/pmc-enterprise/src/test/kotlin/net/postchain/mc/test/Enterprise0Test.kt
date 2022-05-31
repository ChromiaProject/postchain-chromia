package net.postchain.mc.test

import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.enterprise0.CliExecutionE0
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths
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
    @BeforeEach
    fun setup() {
        val resourceDirectory = Paths.get("target", "enterprise0", "rell")
        blockchain0ConfigGtv = run(runXmlFile(), resourceDirectory.toFile())
        doAndBuildBlocks(provConfig, provExecutor.initInternal())
    }

    @Test
    fun testProposeEnableDisableProvider() {

        //First provider adds node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

//        Then proposes a second provider
        doAndBuildBlocks(clientConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))

        voteYes("register_provider")
        assertProviderData(prov2Config.pubKey, "", false)

        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderInternal(prov2Config.pubKey))
        voteYes("provider_state")
        assertProviderEnabled(prov2Config.pubKey)


        // The new provider adds node 1 and node 2
        addNode(prov2Config, node1Pubkey, node1Host, node1Port)
        addNode(prov2Config, node2Pubkey, node2Host, node2Port)

        // prov2 makes node1 a signer of bc0
        doAndBuildBlocks(prov2Config, prov2Executor.proposeAddBlockchainSignersInternal(clientConfig.brid, node1Pubkey))
        var id = assertProposalTypeAndGetRowid("bc_signers")
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, true))
        doAndBuildBlocks(prov2Config, prov2Executor.voteInternal(id, true))

        //prov2 makes node2 a replica of bc0 (no voting needed)
        doAndBuildBlocks(prov2Config, prov2Executor.addReplicaInternal(clientConfig.brid, node2Pubkey))

        //Disable provider:
        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderInternal(prov2Config.pubKey))
        id = assertProposalTypeAndGetRowid("provider_state")
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, true))
        doAndBuildBlocks(prov2Config, prov2Executor.voteInternal(id, true))
        assertProviderDisabled(prov2Config.pubKey)
    }

    @Test
    fun testProposeProviderVoteNo() {
        doAndBuildBlocks(clientConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))

        voteNo("register_provider")
        assertEquals(1, provExecutor.listProviders().size, "")
    }

    @Test
    fun testProposeAddBlockchainXml() {

        //add node0 and bc0
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        //propose new bc:
//        val bcFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileName
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainInternal(bcConfig1xmlFile, nodes[0].pubKey, "xml"))
        voteYes("bc")
        assertBc0Added(clientConfig)
    }

    @Test
    fun testProposeConfigurationAcceptGtv() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, bcConfigGtvFile,
                20L, "gtv"))
        voteYes("conf")
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testProposeConfiguration() {

        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, bcConfig1xmlFile, 20L, "xml"))
        voteYes("conf")
        assertNextConfiguration(clientConfig, 20L)
    }

    private fun voteYes(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, true))
    }

    private fun voteNo(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, false))
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & node2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, signers_list))

        voteYes("bc_signers")
        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + init + 3*addNode + proposeBlockhain + vote + addSigners + vote + 5 = 12
        assertNextConfiguration(clientConfig, 12L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(2)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                doAndBuildBlocks(provConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))
                true
            }
        }
    }

    @Test
    fun testProposeRemoveBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & 2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        addBcSigners(signers_list)

        doAndBuildBlocks(provConfig, provExecutor.proposeRemoveBlockchainSignersInternal(clientConfig.brid, signers_list))
        voteYes("bc_signers")
        val listBlockchainSigners = provExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {
        val executor = cliExecution(clientConfig)
        initAndNode1Replica()

        doAndBuildBlocks(provConfig, provExecutor.proposeStopBlockchainInternal(clientConfig.brid, true))
        voteYes("bc_stop")

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
        assertEquals(3, h)
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
        addNode0(provConfig)
        val version = provExecutor.getNodeListVersion()
        assertTrue(version > 0)
    }

    @Test
    fun testRemoveNode() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        var nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertTrue(nodeInfo["active"]!!.asBoolean())

        // Remove node1
        doAndBuildBlocks(clientConfig, provExecutor.removeNodeInternal(node1Pubkey))

        nodeInfo = provExecutor.getNodeInfo(node1Pubkey).asDict()
        assertFalse(nodeInfo["active"]!!.asBoolean())
    }

    @Test
    fun testListNodes() {
        addNode0(provConfig)
        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        assertListNodes()

    }

    @Test
    fun testListNodesWithProvider() {
        addNode0(provConfig)
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
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addReplicaInternal(clientConfig.brid, node1Pubkey),5)
        assertBlockchainReplica(clientConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, node1Pubkey))
        voteYes("bc_signers")

        val listBlockchainSigners = provExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testGetProposal() {
        doAndBuildBlocks(clientConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))
        val type = "register_provider"
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

    override fun addBc(blockchain0ConfigGtv: Gtv) {
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainGtvInternal(blockchain0ConfigGtv, nodes[0].pubKey))
        voteYes("bc")
    }

    //First (the only) provider adds signers and vote yes to apply the change. This is not a general function.
    override fun addBcSigners(nodeList: String) {
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, nodeList))
        voteYes("bc_signers")
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