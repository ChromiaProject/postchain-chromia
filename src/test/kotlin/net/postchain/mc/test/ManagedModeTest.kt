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
import net.postchain.devtools.PostchainTestNode
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig
import net.postchain.mc.config.app.ClientConfig
import net.postchain.mc.config.app.DelegatingClientConfig
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.MapConfiguration
import org.junit.Assert
import java.nio.file.Paths
import kotlin.test.assertEquals

abstract class ManagedModeTest : RellIntegrationTest() {
//    val clientConfigMap = mapOf(
//            Pair("pubkey", "0373599a61cc6b3bc02a78c34313e1737ae9cfd56b9bb24360b437d469efdf3b15"),
//            Pair("privkey", "a68957ba735f98f8d8169ee54fccf2ccf193d97d95e46342bb5e05007c51f324")
//    )
//    val provConfigMap = mapOf(
//            Pair("pubkey", "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"),
//            Pair("privkey", "9EC6477E36921F519BC2F805BFA01E9D2AE9DFF2D761A1140C76EEEAFEC78453")
//    )
//    val prov2ConfigMap = mapOf(
//            Pair("pubkey", "039622229BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"),
//            Pair("privkey", "9EC2227E36921F519BC2F805BFA01E9D2AE9DFF2D761A1140C76EEEAFEC78453")
//    )
    val node1Pubkey = KeyPairHelper.pubKeyHex(1)
    val node1Host = "127.0.0.1"
    val node1Port = 9871L
    val blockSignerKeyNode0 = 0
    val adminKey = 10
    val providerKey = 11
    val providerKey2 = 12
    val clientConfig = cliConf(adminKey)
    val provConfig = cliConf(providerKey)
    val prov2Config = cliConf(providerKey2)
    open val provExecutor = cliExecution(provConfig)
    open val prov2Executor = cliExecution(prov2Config)
    open val adminExecutor = cliExecution(clientConfig)

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

    protected fun cliConf(basedOn: Map<String, Any>): ClientConfig {
        return cliConf(MapConfiguration(basedOn))
    }

    protected fun getPostchainClient(appConfig: ClientConfig): PostchainClient {
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(appConfig.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(appConfig.pubKey.hexStringToByteArray(), appConfig.privKey.hexStringToByteArray())
        val defaultSigner = DefaultSigner(sigMaker, appConfig.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(appConfig.brid), defaultSigner)
    }

//    protected fun createNode(nodeIndex: Int, nodeCount: Int, nodeConfigFile: String, blockchainConfigXmlFile: String): PostchainTestNode {
////        run("chroma0")
//        return nodes[nodeIndex]
////        return createSingleNode(nodeIndex, nodeCount, nodeConfigFile, blockchainConfigXmlFile) { appConfig, n ->
////            runStorageCommand(appConfig) {
////                DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
////                        .addPeerInfo(it, TestPeerInfos.peerInfo0)
////            }
////        }
//    }
//
//    protected fun createNode(nodeIndex: Int, nodeCount: Int, configFileName: String): PostchainTestNode {
////        return createNode(nodeIndex, nodeCount, DEFAULT_CONFIG_FILE, configFileName)
//        return createNode(nodeIndex, nodeCount, configFileName)
//    }
//
//    protected fun createNode(configFileName: String, nodeCount: Int): PostchainTestNode {
//        val nodeIndex = 0
//        return createNode(nodeIndex, nodeCount, configFileName)
//    }
//
//    protected fun createNode(configFileName: String): PostchainTestNode {
//
//        return createNode(configFileName, 1)
//    }

    abstract fun cliExecution(cliConfig: ClientConfig): CliExecution


    protected fun assertProviderData(config: ClientConfig, provPubkey: String, name: String) {

        val client = getPostchainClient(config)
        val provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(provPubkey.hexStringToByteArray()))).get()
        val data = provider.asDict()
        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), provPubkey.hexStringToByteArray())
        assertk.assert(data["name"]?.asString()).isEqualTo(name)
        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
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

//    Help function used in tests for system setup. Node0 is added as signer and blockchain 0 is added, so that becomes aware of itself. So that it can be managed.
    protected fun addProviderAndNode0AndBlockchain(configFileName: String, config: ClientConfig, configProv: ClientConfig) {
        addProviderAndNode0(config, configProv)

        val executor = cliExecution(config)
        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
                + "/src/test/resources" + configFileName, nodes[0].pubKey, "xml")
        assertBlockchainAdded(config)
    }

    fun addProviderAndNode0(config: ClientConfig, configProv: ClientConfig) {

        val providerPublicKey = configProv.pubKey
        val executor = cliExecution(config)
        executor.registerProvider(providerPublicKey)
        assertProviderData(config, providerPublicKey, "")

        // provider active status should be true after calling enable
        executor.enableProvider(providerPublicKey)
        assertProviderEnabled(config, providerPublicKey)

        addNode0(configProv)
    }

    //    Help function used in tests for system setup. Node0 is added as signer and blockchain 0 is added, so that becomes aware of itself. So that it can be managed.
    protected fun addNode0AndBlockchain(configFileName: String, config: ClientConfig, configProv: ClientConfig) {
        addNode0(configProv)

        val executor = cliExecution(config)
        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
                + "/src/test/resources" + configFileName, nodes[0].pubKey, "xml")
        awaitBlockchainReload()

        assertBlockchainAdded(config)
    }


    fun addNode0(configProv: ClientConfig) {

        val providerPublicKey = configProv.pubKey
        cliExecution(configProv).addNode(nodes[0].pubKey, "127.0.0.1", 9870L)
        awaitBlockchainReload()
        assertAddedNode(configProv, providerPublicKey, host = "127.0.0.1", port = 9870L)
    }

    fun assertBlockchainAdded(config: ClientConfig) {
        val client = getPostchainClient(config)
        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()

        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
    }

    fun assertAddedNode(config: ClientConfig, providerPublicKey: String, host: String, port: Long) {
        val client = getPostchainClient(config)
        val node = client.query("get_node_data", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(nodes[0].pubKey.hexStringToByteArray()))).get().asDict()
        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
        assertk.assert(node["host"]?.asString()).isEqualTo(host)
        assertk.assert(node["port"]?.asInteger()).isEqualTo(port)
        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), nodes[0].pubKey.hexStringToByteArray())
    }


    fun assertListNodes(clientConfig: ClientConfig) {
        val executor = cliExecution(clientConfig)
        val nodesList = executor.listNodesWithProvider()
        val n = nodesList[0].asDict()
        assertEquals("127.0.0.1", n["host"]!!.asString())
        assertEquals(9870L, n["port"]!!.asInteger())
        assertEquals(nodes[0].pubKey, n["pubkey"]!!.asByteArray().toHex())

        assertEquals(clientConfig.pubKey, n["provider"]!!.asByteArray().toHex())
        assertEquals(true, n["provider_active"]!!.asBoolean())
    }


    /*
        * Initialization function that adds node0 with configuration from  config.properties. Also adds blockchain. This is
        * addNode0AndBlockchain. Also node1 is added both as repica and signer to this bc.
        * */
    protected fun initAndNode1ReplicaAndSigner(configFileName: String) {
        addProviderAndNode0AndBlockchain(configFileName, clientConfig, provConfig)

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
        Thread.sleep(8000)
    }

}