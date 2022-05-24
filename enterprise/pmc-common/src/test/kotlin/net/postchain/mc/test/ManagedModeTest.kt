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
import net.postchain.mc.PrintUtils
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
    open val adminExecutor = cliExecution(clientConfig)

    lateinit var blockchain0ConfigGtv: Gtv

    companion object {
        val bcConfig1xmlFile = getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_1.xml")
        val bcConfigGtvFile = getFileFromClasspath("/net/postchain/mc/test/config/0.gtv")

        private fun getFileFromClasspath(path: String): File {
            val tempFile = File.createTempFile("managed-mode-test", "")
            val inputStream = Companion::class.java.getResourceAsStream(path)!!
            tempFile.writeBytes(inputStream.readAllBytes())
            return tempFile
        }
    }

    protected fun getPostchainClient(appConfig: ClientConfig): PostchainClient {
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(appConfig.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(appConfig.pubKey.hexStringToByteArray(), appConfig.privKey.hexStringToByteArray())
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

    protected fun assertNextConfiguration(config: ClientConfig, expectedHeight: Long) {

        // Get next configuration height of new blockchain configuration
        val client = getPostchainClient(config)
        val height = client.query("nm_find_next_configuration_height", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to GtvFactory.gtv(0L))).get()
        assertk.assert(height.asInteger()).isEqualTo(expectedHeight)

        // Get next configuration
        val bc = client.query("nm_get_blockchain_configuration", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to height)).get()
        assertk.assert(bc.asByteArray()).isNotNull()
    }


    /* Function used in tests for system setup. Node0 is added as signer and blockchain 0 is added, so that becomes
    * aware of itself. So that it can be managed.
    */
    protected fun addNode0AndBc0(blockchain0ConfigGtv: Gtv, config: ClientConfig, configProv: ClientConfig) {
        addNode0(configProv)
        addBc(blockchain0ConfigGtv)
        assertBc0Added(config)
    }

    abstract fun addBc(blockchain0ConfigGtv: Gtv)

    fun addNode(configProv: ClientConfig, key: String, host: String, port: Long) {
        val executor = cliExecution(configProv)
        executor.sendTxUnconfirmed(executor.addNodeInternal(key, host, port))
        buildAndAwaitBlocks(1)
        assertAddedNode(configProv, configProv.pubKey, key, host, port)
    }

    fun addNode0(configProv: ClientConfig) {
        addNode(configProv, nodes[0].pubKey, node0Host, node0Port)
    }

    fun assertBc0Added(config: ClientConfig) {
        assertBcAdded(config, config.brid.hexStringToByteArray())
    }

    fun assertBcAdded(config: ClientConfig, bridByteArray: ByteArray) {
        val client = getPostchainClient(config)
        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
                "rid" to GtvFactory.gtv(bridByteArray))).get()

        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
    }

    fun assertAddedNode(config: ClientConfig, providerPublicKey: String, nodePubkey: String, host: String, port: Long) {
        val client = getPostchainClient(config)
        val node = client.query("get_node_data", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(nodePubkey.hexStringToByteArray()))).get().asDict()
        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
        assertk.assert(node["host"]?.asString()).isEqualTo(host)
        assertk.assert(node["port"]?.asInteger()).isEqualTo(port)
        assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
        assertArrayEquals(node["pubkey"]?.asByteArray(), nodePubkey.hexStringToByteArray())
    }


    /*
        * Initialization function that adds node0 with configuration from  config.properties. It also adds blockchain (This is
        * function addNode0AndBlockchain). Finally, node1 is added as replica.
        * */
    protected fun initAndNode1Replica() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        //add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // make node 1 a replica
        provExecutor.sendTxUnconfirmed(provExecutor.addReplicaInternal(clientConfig.brid, node1Pubkey))
        buildAndAwaitBlocks(5)

        val replicas = provExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, replicas.size)
    }

    // nodelist: comma-separated list of node pubkeys
    abstract fun addBcSigners(nodeList: String)

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
        PrintUtils.printBlockchainNodes(listReplicas)
    }

    fun assertListNodesNode0(provConfig: ClientConfig) {
        val provExecutor = cliExecution(provConfig)
        val nodesList = provExecutor.listNodesWithProvider()
        val n = nodesList[0].asDict()
        assertEquals(node0Host, n["host"]!!.asString())
        assertEquals(node0Port, n["port"]!!.asInteger())
        assertEquals(nodes[0].pubKey, n["pubkey"]!!.asByteArray().toHex())

        assertEquals(provConfig.pubKey, n["provider"]!!.asByteArray().toHex())
        assertEquals(true, n["provider_active"]!!.asBoolean())
        PrintUtils.printNodes(nodesList)
    }

    protected fun assertListNodes() {
        val nodeList = provExecutor.listNodes()
        val n0 = nodeList.get(0)
        assertEquals(node0Host, n0.get(0).asString())
        assertEquals(node0Port, n0.get(1).asInteger())
        assertEquals(nodes[0].pubKey, n0.get(2).asByteArray().toHex())

        val n1 = nodeList.get(1)
        assertEquals(node1Host, n1.get(0).asString())
        assertEquals(node1Port, n1.get(1).asInteger())
        assertEquals(node1Pubkey, n1.get(2).asByteArray().toHex())
    }

    protected fun buildAndAwaitBlock() {
        buildAndAwaitBlocks(1)
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

    protected fun assertListNodesByProvider() {
        val nodeList = provExecutor.listNodesByProvider(provConfig.pubKey)
        assertEquals(2, nodeList.size)
        val node0 = nodeList[0].asDict()
        assertEquals(nodes[0].pubKey.toUpperCase(), node0["pubkey"]!!.asByteArray().toHex())
        assertEquals(node1Pubkey, nodeList[1].asDict()["pubkey"]!!.asByteArray().toHex())
        PrintUtils.printNodes(nodeList, true, false)
    }

}