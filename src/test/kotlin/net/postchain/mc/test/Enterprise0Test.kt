package net.postchain.mc.test

import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.enterprise0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Enterprise0Test : ManagedModeTest() {

    override fun chainConfSnippet(): String{
        val module = "bc0"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="${module}">
                            <args module="${module}">
                                <arg key="admin"><bytea>${KeyPairHelper.pubKeyHex(adminKey)}</bytea></arg>
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
        return CliExecution(cliConfig)
    }

    val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
    override val adminExecutor = CliExecution(clientConfig)
    override val provExecutor = CliExecution(provConfig)
    override val prov2Executor = CliExecution(prov2Config)


    /*
    * The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. Operation init() registers and enables the module argument
    * `initial_provider` as provider. So that we have an initial voter.
    * */
    @Before
    fun setup() {
        blockchain0ConfigGtv = run("enterprise0")
        doAndBuildBlocks(clientConfig, adminExecutor.initInternal())
    }

    @Test
    fun testProposeEnableDisableProvider() {
        doAndBuildBlocks(clientConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))

        voteYes("register_provider")
        assertProviderData(clientConfig, prov2Config.pubKey, "", false)

        doAndBuildBlocks(provConfig, provExecutor.proposeEnableProviderInternal(prov2Config.pubKey))
        voteYes("provider_state")
        assertProviderEnabled(clientConfig, prov2Config.pubKey)

        doAndBuildBlocks(provConfig, provExecutor.proposeDisableProviderInternal(prov2Config.pubKey))
        val id = assertProposalTypeAndGetRowid("provider_state")
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, true))
        doAndBuildBlocks(prov2Config, prov2Executor.voteInternal(id, true))
        assertProviderDisabled(clientConfig, prov2Config.pubKey)
    }

    @Test
    fun testProposeAddBlockchainXml() {

        //add node0 and bc0
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        //propose new bc:
        val bcFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileName
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainInternal(bcFile, nodes[0].pubKey, "xml"))
        voteYes("bc")
        assertBlockchain0Added(clientConfig)
    }

    @Test
    fun testProposeConfigurationAcceptGtv() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/0.gtv"
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, blockchainConfigFile,
                20L, "gtv"))
        voteYes("conf")
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testProposeConfiguration() {

        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
        doAndBuildBlocks(provConfig, provExecutor.proposeConfigurationInternal(clientConfig.brid, blockchainConfigFile, 20L, "xml"))
        voteYes("conf")
        assertNextConfiguration(clientConfig, 20L)
    }

    private fun voteYes(proposalType: String) {
        val id = assertProposalTypeAndGetRowid(proposalType)
        doAndBuildBlocks(provConfig, provExecutor.voteInternal(id, true))
    }

    @Test(expected = org.awaitility.core.ConditionTimeoutException::class)
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & node2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, signers_list))

        voteYes("bc_signers")
        // Get next configuration height after adding new node as blockchain's signer
        // TODO: This is supposed to be 10, but a change in rell 0.10.3 causes it to be 9. We should fix our
        // module0 accordingly. See https://chromadev.zulipchat.com/#narrow/stream/144497-postchain-core-dev/topic/Chromia0/near/211956706
        // So why is it here 10 when tow signers are added and 9 when only one signer is added?
        assertNextConfiguration(clientConfig, 10L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(3)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        Awaitility.await().atMost(Duration.ONE_SECOND).until {
            doAndBuildBlocks(provConfig, provExecutor.proposeProviderInternal(prov2Config.pubKey))
            true
        }
    }

    @Test
    fun testProposeRemoveBlockchainSigners() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & 2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainSignersInternal(clientConfig.brid, signers_list))

        doAndBuildBlocks(provConfig, provExecutor.proposeRemoveBlockchainSignersInternal(clientConfig.brid, signers_list))

        voteYes("bc_signers")
        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {
        val executor = cliExecution(clientConfig)
        initAndNode1ReplicaAndSigner()

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
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val listBlockchains = adminExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testGetBlockchainConfiguration() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchain = adminExecutor.getBlockchainConfiguration(clientConfig.brid, 0L)
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
    fun testListNodes() {
        addNode0(provConfig)
        // Add node1 to managed blockchain
//        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
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
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val listBlockchains = adminExecutor.listAllBlockchains()
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainReplicas() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addReplicaInternal(clientConfig.brid, node1Pubkey),5)
        assertBlockchainReplica(clientConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(provConfig, provExecutor.proposeAddBlockchainSignersInternal(clientConfig.brid, node1Pubkey))
        voteYes("bc_signers")

        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    //    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: String): Long {
        val client = getPostchainClient(clientConfig)
        val proposals = client.query("get_proposals_since", GtvFactory.gtv(
                "since" to GtvFactory.gtv(0L))).get()
        val type = (proposals[0].asDict()["proposal_type"] as GtvString).string
        val id = (proposals[0].asDict()["rowid"] as GtvInteger).asInteger()
        assertEquals(expectedType, type, "Wrong proposal type")
        return id
    }
}