package net.postchain.mc.test

import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.base.BlockchainRid
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig
import net.postchain.mc.config.app.ClientConfig
import net.postchain.mc.config.app.DelegatingClientConfig
import org.apache.commons.configuration2.Configuration
import org.junit.Assert
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class ManagedModeTest : RellIntegrationTest() {

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

    protected fun cliConf(basedOn: Configuration): ClientConfig {
        return cliConf(0, basedOn)
    }

    protected fun cliConf(index: Int, basedOn: Configuration): ClientConfig {
        return object : DelegatingClientConfig(BaseClientConfig(basedOn)) {
            override val apiURL: String
                get() = "http://127.0.0.1:" + nodes[index].getRestApiHttpPort()

            override val brid: String
                get() = nodes[index].getBlockchainRid(0)!!.toHex()
        }
    }

//    protected fun cliConf(basedOn: Map<String, Any>): ClientConfig {
//        return cliConf(MapConfiguration(basedOn))
//    }

    protected fun getPostchainClient(appConfig: ClientConfig): PostchainClient {
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(appConfig.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(appConfig.pubKey.hexStringToByteArray(), appConfig.privKey.hexStringToByteArray())
        val defaultSigner = DefaultSigner(sigMaker, appConfig.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(appConfig.brid), defaultSigner)
    }

    abstract fun cliExecution(cliConfig: ClientConfig): CliExecution


    protected fun assertProviderData(config: ClientConfig, provPubkey: String, name: String, isActive: Boolean?) {

        val client = getPostchainClient(config)
        val provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(provPubkey.hexStringToByteArray()))).get()
        val data = provider.asDict()
        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), provPubkey.hexStringToByteArray())
        assertk.assert(data["name"]?.asString()).isEqualTo(name)
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(config: ClientConfig, providerPublicKey: String) {
        val client = getPostchainClient(config)
        val provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
        val data = provider.asDict()
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(true)
    }

    protected fun assertProviderDisabled(config: ClientConfig, providerPublicKey: String) {
        val client = getPostchainClient(config)
        val provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
        val data = provider.asDict()
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
    }

    protected fun assertAddConfiguration(config: ClientConfig, expectedHeight: Long) {

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
    protected fun addNode0AndBlockchain0(blockchain0ConfigGtv: Gtv, config: ClientConfig, configProv: ClientConfig) {
        addNode0(configProv)

        adminExecutor.addBlockchainGtv(blockchain0ConfigGtv, nodes[0].pubKey)
//        adminExecutor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, nodes[0].pubKey, "xml")
        awaitBlockchainReload()
        assertBlockchain0Added(config)
    }


    fun addNode0(configProv: ClientConfig) {

        cliExecution(configProv).addNode(nodes[0].pubKey, node0Host, node0Port)
        awaitBlockchainReload()
        assertAddedNode(configProv, configProv.pubKey, nodes[0].pubKey, host = node0Host, port = node0Port)
    }

    fun assertBlockchain0Added(config: ClientConfig) {
        assertBlockchainAdded(config, config.brid.hexStringToByteArray())
//        val client = getPostchainClient(config)
//        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
//                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()
//
//        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
    }

    fun assertBlockchainAdded(config: ClientConfig, bridByteArray: ByteArray) {
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
        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), nodePubkey.hexStringToByteArray())
    }





    /*
        * Initialization function that adds node0 with configuration from  config.properties. Also adds blockchain. This is
        * addNode0AndBlockchain. Also node1 is added both as repica and signer to this bc.
        * */
    protected fun initAndNode1ReplicaAndSigner() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
        awaitBlockchainReload()

        // add replicas
        provExecutor.addReplica(clientConfig.brid, node1Pubkey)

        val replicas = adminExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, replicas.size)

        // Add node1 as blockchain's signer
        adminExecutor.addBlockchainSigners(clientConfig.brid, node1Pubkey)

        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    fun awaitBlockchainReload() {
//        Awaitility.await().atMost(Duration.ONE_MINUTE)
//                .untilAsserted {
//                    assertk.assert(nodes[0].getModules(0L)).isNotEmpty()
//                    assertk.assert(nodes[0].getModules(0L).first())
//                            .isInstanceOf(ManagedTestModuleReconfiguring2::class)
//                }

        Thread.sleep(2000)
    }

    protected fun assertBlockchainReplica(clientConfig: ClientConfig, nodePubkey: String, host: String, port: Long) {
        val executor = cliExecution(clientConfig)
        val listBlockchains = executor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, listBlockchains.size)
        val bc = listBlockchains.get(0).asArray()
        assertEquals(clientConfig.brid, bc.get(0).asByteArray().toHex())
        assertEquals(nodePubkey, bc.get(1).asByteArray().toHex())
        assertEquals(host, bc.get(2).asString())
        assertEquals(port, bc.get(3).asInteger())
        assertTrue(bc.get(4).asBoolean())
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

}