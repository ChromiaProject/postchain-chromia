package net.postchain.mc.test

import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.util.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class ManagedModeTest : RellIntegrationTest() {

    val node2Pubkey = KeyPairHelper.pubKeyHex(2)
    val node2Host = "127.0.0.1"
    val node2Port = 9872L

    val node1Pubkey = KeyPairHelper.pubKeyHex(1)
    val node1Host = "127.0.0.1"
    val node1Port = 9871L

    val node0Host = "127.0.0.1"
    val node0Port = 9870L
    val node0BlockSignerKey = 0

    val adminKey = 10
    val providerKey = 11
    val providerKey2 = 12
    val clientConfig = cliConf(adminKey)
    val provConfig = cliConf(providerKey)
    val prov2Config = cliConf(providerKey2)
    open val provExecutor = cliExecution(provConfig)
    open val prov2Executor = cliExecution(prov2Config)
    lateinit var blockchain0ConfigGtv: Gtv

    // Voter sets SYSTEM and SYSTEM_P are created during initialization.
    val voterSetSystemP = "SYSTEM_P"
    val voterSetSystem = "SYSTEM"
    val systemClusterName = "system"
    val systemContainerName = "system"

    companion object {
        val bcConfig1xmlFile = getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_1.xml")
        val bcConfig1xmlDependencyFile =
                getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_1_dependency.xml")
        val bcConfigGtvFile = getFileFromClasspath("/net/postchain/mc//test/config/0.gtv")

        private fun getFileFromClasspath(path: String): File {
            val tempFile = File.createTempFile("managed-mode-test", "")
            val inputStream = Companion::class.java.getResourceAsStream(path)!!
            tempFile.writeBytes(inputStream.readAllBytes())
            return tempFile
        }
    }

    protected fun getPostchainClient(appConfig: ClientConfig): PostchainClient {
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(appConfig.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(
                appConfig.pubKey.hexStringToByteArray(),
                appConfig.privKey.hexStringToByteArray()
        )
        val defaultSigner = DefaultSigner(sigMaker, appConfig.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(appConfig.brid), defaultSigner)
    }

    abstract fun cliExecution(cliConfig: ClientConfig): CliExecution

    protected fun assertProviderData(provPubkey: String, name: String, isActive: Boolean?) {
        val data = provExecutor.getProviderInfo(provPubkey).asDict()
        assertArrayEquals(data["pubkey"]?.asByteArray(), provPubkey.hexStringToByteArray())
        assertk.assert(data["name"]?.asString()).isEqualTo(name)
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey).asDict()
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(true)
    }

    protected fun assertProviderDisabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey).asDict()
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
        val listReplicas = provExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(0, listReplicas.size)

        val listSigners = provExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(1, listSigners.size)
    }

    protected fun assertNextConfiguration(config: ClientConfig, expectedHeight: Long): ByteArray {

        // Get next configuration height of new blockchain configuration
        val client = getPostchainClient(config)
        val height = client.querySync(
                "nm_find_next_configuration_height", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to GtvFactory.gtv(0L)
        )
        )
        assertk.assert(height.asInteger()).isEqualTo(expectedHeight)

        // Get next configuration
        val bc = client.querySync(
                "nm_get_blockchain_configuration", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to height
        )
        )
        assertk.assert(bc.asByteArray()).isNotNull()
        return bc.asByteArray()
    }


    /** Function used in tests for system setup. Node0 is added as signer and blockchain 0 is added, so that becomes
     * aware of itself. So that it can be managed. Bc0 is added to naked system container. Node0 is added to
     * system cluster so that it becomes signer.
     */
    protected fun addNode0AndBc0(blockchain0ConfigGtv: Gtv, configProv: ClientConfig) {
        addNode0(configProv, systemClusterName)
        addBc(blockchain0ConfigGtv, systemContainerName)
        assertAdded("get_blockchain", "rid", GtvFactory.gtv(configProv.brid.hexStringToByteArray()))
    }

    fun addBc(blockchain0ConfigGtv: Gtv, container: String) {
        doAndBuildBlocks(provConfig, provExecutor.proposeBlockchainGtvAsync(blockchain0ConfigGtv, container))
    }

    /**
     * Add node; optionally also add it to a cluster
     */
    fun addNode(configProv: ClientConfig, key: String, host: String, port: Long, clusterName: String) {
        val executor = cliExecution(configProv)
        executor.sendTxUnconfirmed(executor.addNodeAsync(key, host, port, clusterName))
        buildAndAwaitBlocks(1)
        assertAddedNode(configProv.pubKey, key, host, port, clusterName)
    }

    /**
     * Add node0; optionally also add it to a cluster
     */
    fun addNode0(configProv: ClientConfig, clusterName: String) {
        addNode(configProv, nodes[0].pubKey, node0Host, node0Port, clusterName)
    }

    fun assertAdded(opName: String, keyName: String, addedItem: Gtv) {
        val client = getPostchainClient(provConfig)
        val vs = client.querySync(opName, GtvFactory.gtv(keyName to addedItem))
        assertk.assert(vs.asInteger()).isGreaterThan(0L)
    }

    fun assertAddedNode(providerPublicKey: String, nodePubkey: String, host: String, port: Long, cluster: String) {
        awaitUntilAsserted {
            var nodeInfo = provExecutor.getNodeInfo(nodePubkey).asDict()
            assertk.assert(nodeInfo["active"]?.asBoolean()).isEqualTo(true)
            assertk.assert(nodeInfo["host"]?.asString()).isEqualTo(host)
            assertk.assert(nodeInfo["port"]?.asInteger()).isEqualTo(port)
            assertArrayEquals(nodeInfo["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
            assertArrayEquals(nodeInfo["pubkey"]?.asByteArray(), nodePubkey.hexStringToByteArray())
            if (cluster != "") {
                assertEquals(nodeInfo["cluster"]?.asArray()?.map { it.asString() }, listOf(cluster))
            }
        }
    }

    /** Initialization function that adds node0 with configuration from  config.properties. It also adds blockchain (This is
     * function addNode0AndBlockchain). Finally, node1 is added as replica for bc0.
     * */
    protected fun initAndNode1ReplicaOfBc0() {
        addNode0AndBc0(blockchain0ConfigGtv, provConfig)

        //add node1 (no specified cluster)
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // make node 1 a replica for bc0
        provExecutor.sendTxUnconfirmed(provExecutor.addBlockchainReplicaAsync(clientConfig.brid, node1Pubkey))
        buildAndAwaitBlocks(5)

        val replicas = provExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, replicas.size)
    }

//    // nodelist: comma-separated list of node pubkeys

    fun awaitBlockchainReload() {
//        Awaitility.await().atMost(Duration.ONE_MINUTE)
//                .untilAsserted {
//                    assertk.assert(nodes[0].getModules(0L)).isNotEmpty()
//                    assertk.assert(nodes[0].getModules(0L).first())
//                            .isInstanceOf(ManagedTestModuleReconfiguring2::class)
//                }
//        buildAnd
        Thread.sleep(2000)
    }

    protected fun assertBlockchainReplica(clientConfig: ClientConfig, nodePubkey: String, host: String, port: Long) {
        val executor = cliExecution(clientConfig)
        val listReplicas = executor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, listReplicas.size)
        val bc = listReplicas.get(0).asArray()
        assertEquals(clientConfig.brid, bc.get(0).asByteArray().toHex())
        assertEquals(nodePubkey, bc.get(1).asByteArray().toHex())
        assertEquals(host, bc.get(2).asString())
        assertEquals(port, bc.get(3).asInteger())
        assertTrue(bc.get(4).asBoolean())
        PrintUtils.printBlockchainReplicas(listReplicas)
    }

    private fun assertNodeInfo(
            n: Gtv,
            nodeHost: String,
            nodePort: Long,
            nodePubkey: String,
            providerPubkey: String,
            b: Boolean
    ) {
        val nodeDict = n.asDict()
        assertEquals(nodeHost, nodeDict["host"]!!.asString())
        assertEquals(nodePort, nodeDict["port"]!!.asInteger())
        assertEquals(nodePubkey, nodeDict["pubkey"]!!.asByteArray().toHex())

        assertEquals(providerPubkey, nodeDict["provider"]!!.asByteArray().toHex())
        assertEquals(b, nodeDict["provider_active"]!!.asBoolean())
    }

    protected fun assertListNodes() {
        val providerNodes = provExecutor.listNodesByProvider(provConfig.pubKey)
        assertEquals(2, providerNodes.size)

        val nodeList = provExecutor.listNodesWithProvider()
        assertNodeInfo(nodeList[0], node0Host, node0Port, nodes[0].pubKey, provConfig.pubKey, true)
        assertNodeInfo(nodeList[1], node1Host, node1Port, node1Pubkey, provConfig.pubKey, true)

        PrintUtils.printNodes(nodeList)

    }

    protected fun buildAndAwaitBlocks(nBlocks: Int) {
        val strat = strategy()
        val height = strat.committedHeight + nBlocks
        strat.buildBlocksUpTo(height.toLong())
        strat.awaitCommitted(height)
        awaitBlockchainReload()
    }

    private fun strategy(): SmartOnDemandBlockBuildingStrategy {
        val strat = nodes[0].blockBuildingStrategy(0) as SmartOnDemandBlockBuildingStrategy
        return strat
    }

    fun doAndBuildBlocks(clientConfig: ClientConfig, f: GTXTransactionBuilder, nBlocks: Int = 1) {
        val executor = cliExecution(clientConfig)
        executor.sendTxUnconfirmed(f)
        buildAndAwaitBlocks(nBlocks)
    }

}