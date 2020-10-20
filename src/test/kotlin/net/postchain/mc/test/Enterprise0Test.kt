package net.postchain.mc.test

import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.enterprise0.CliExecution
import net.postchain.mc.config.app.ClientConfig
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
    *
    * */
    @Before
    fun setup() {
        blockchain0ConfigGtv = run("enterprise0")
        adminExecutor.init()
    }

    @Test
    fun testProposeAddBlockchain() {

        //add node0 and bc0
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        //propose new bc:
        val bcFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileName
        provExecutor.proposeBlockchain(bcFile, nodes[0].pubKey, "xml")

        val id = assertProposalTypeAndGetRowid("bc")
        provExecutor.vote(id, true)
        assertBlockchain0Added(clientConfig)
    }

    @Test
    fun testProposeConfigurationAndVote() {

        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
        provExecutor.proposeConfiguration(clientConfig.brid, blockchainConfigFile, 20L, "xml")

        val id = assertProposalTypeAndGetRowid("conf")
        provExecutor.vote(id, true)
        assertAddConfiguration(clientConfig, 20L)
    }

    @Test
    fun testStopBlockchain() {

        val executor = cliExecution(clientConfig)
        initAndNode1ReplicaAndSigner()

        provExecutor.proposeStopBlockchain(clientConfig.brid, true)

        val id = assertProposalTypeAndGetRowid("bc_stop")
        provExecutor.vote(id, true)

        // query replicas again to ensure it was deleted after stop blockchain
        val replicas = executor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(0, replicas.size)

        // query signers again to ensure it was deleted after stop blockchain
        val listBlockchainSigners = executor.listBlockchainSigners(clientConfig.brid)
        assertEquals(0, listBlockchainSigners.size)

    }

    @Test
    fun testProposeEnableDisableProvider() {
        provExecutor.proposeProvider(prov2Config.pubKey)
        var id = assertProposalTypeAndGetRowid("register_provider")
        provExecutor.vote(id, true)
        assertProviderData(clientConfig, prov2Config.pubKey, "", false)

        provExecutor.proposeEnableProvider(prov2Config.pubKey)
        id = assertProposalTypeAndGetRowid("provider_state")
        provExecutor.vote(id, true)
        assertProviderEnabled(clientConfig, prov2Config.pubKey)

        provExecutor.proposeDisableProvider(prov2Config.pubKey)
        id = assertProposalTypeAndGetRowid("provider_state")
        provExecutor.vote(id, true)
        prov2Executor.vote(id, true)
        assertProviderDisabled(clientConfig, prov2Config.pubKey)
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
        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
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
        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
        awaitBlockchainReload()

        // add node 1 as replica
        provExecutor.addReplica(clientConfig.brid, node1Pubkey)

        assertBlockchainReplica(clientConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        awaitBlockchainReload()

        // Add node1 to managed blockchain
        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
        awaitBlockchainReload()

        // Add node1 as blockchain's signer
        adminExecutor.addBlockchainSigners(clientConfig.brid, node1Pubkey)

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