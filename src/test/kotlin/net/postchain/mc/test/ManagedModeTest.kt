package net.postchain.mc.test

import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.base.BlockchainRid
import net.postchain.base.data.DatabaseAccessFactory
import net.postchain.base.runStorageCommand
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.devtools.IntegrationTest
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

abstract class ManagedModeTest : IntegrationTest() {
    val clientConfigMap = mapOf(
            Pair("pubkey", "0373599a61cc6b3bc02a78c34313e1737ae9cfd56b9bb24360b437d469efdf3b15"),
            Pair("privkey", "a68957ba735f98f8d8169ee54fccf2ccf193d97d95e46342bb5e05007c51f324")
    )
    val provConfigMap = mapOf(
            Pair("pubkey", "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"),
            Pair("privkey", "9EC6477E36921F519BC2F805BFA01E9D2AE9DFF2D761A1140C76EEEAFEC78453")
    )
    val prov2ConfigMap = mapOf(
            Pair("pubkey", "039622229BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"),
            Pair("privkey", "9EC2227E36921F519BC2F805BFA01E9D2AE9DFF2D761A1140C76EEEAFEC78453")
    )

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

    protected fun createNode(nodeIndex: Int, nodeCount: Int, nodeConfigFile: String, blockchainConfigXmlFile: String): PostchainTestNode {
        return createSingleNode(nodeIndex, nodeCount, nodeConfigFile, blockchainConfigXmlFile) { appConfig, n ->
            runStorageCommand(appConfig) {
                DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
                        .addPeerInfo(it, TestPeerInfos.peerInfo0)
            }
        }
    }

    protected fun createNode(nodeIndex: Int, nodeCount: Int, configFileName: String): PostchainTestNode {
        return createNode(nodeIndex, nodeCount, DEFAULT_CONFIG_FILE, configFileName)
    }

    protected fun createNode(configFileName: String, nodeCount: Int): PostchainTestNode {
        val nodeIndex = 0
        return createNode(nodeIndex, nodeCount, configFileName)
    }

    protected fun createNode(configFileName: String): PostchainTestNode {
        return createNode(configFileName, 1)
    }

    abstract fun cliExecution(cliConfig: ClientConfig): CliExecution


    protected fun assertProviderRegistered(config: ClientConfig, provPubkey: String) {

        val client = getPostchainClient(config)
        val provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(provPubkey.hexStringToByteArray()))).get()
        val data = provider.asDict()
        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), provPubkey.hexStringToByteArray())
        assertk.assert(data["name"]?.asString()).isEqualTo("")
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

    protected fun assertAddConfiguration(config: ClientConfig) {

        // Get next configuration height of new blockchain configuration
        val client = getPostchainClient(config)
        val height = client.query("nm_find_next_configuration_height", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to GtvFactory.gtv(0L))).get()
        assertk.assert(height.asInteger()).isEqualTo(20L)

        // Get next configuration
        val bc = client.query("nm_get_blockchain_configuration", GtvFactory.gtv(
                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to height)).get()
        assertk.assert(bc.asByteArray()).isNotNull()
    }

//    Help function used in tests for system setup. Node0 is added as signer and blockchain 0 is added, so that becomes aware of itself. So that it can be managed.
    protected fun addNodeAndBlockchain(configFileName: String, config: ClientConfig, configProv: ClientConfig): Pair<CliExecution, PostchainClient> {
        // Creating node0
        createNode(configFileName)

        val providerPublicKey = configProv.pubKey
        val executor = cliExecution(config)
        executor.registerProvider(providerPublicKey)
        assertProviderRegistered(config, providerPublicKey)

        // provider active status should be true after calling enable
        executor.enableProvider(providerPublicKey)
        assertProviderEnabled(config, providerPublicKey)

        cliExecution(configProv).addNode(nodes[0].pubKey, "127.0.0.1", 9870L)
        assertAddedNode(config, providerPublicKey, host = "127.0.0.1", port = 9870L)

        Thread.sleep(5000)
        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
                + "/src/test/resources" + configFileName, nodes[0].pubKey, "xml")
        assertBlockchainAdded(config)
        val client = getPostchainClient(config)

        return Pair(executor, client)
    }

    private fun assertBlockchainAdded(config: ClientConfig) {
        val client = getPostchainClient(config)
        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()

        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
    }

    private fun assertAddedNode(config: ClientConfig, providerPublicKey: String, host: String, port: Long) {
        val client = getPostchainClient(config)
        val node = client.query("get_node_data", GtvFactory.gtv(
                "pubkey" to GtvFactory.gtv(nodes[0].pubKey.hexStringToByteArray()))).get().asDict()
        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
        assertk.assert(node["host"]?.asString()).isEqualTo(host)
        assertk.assert(node["port"]?.asInteger()).isEqualTo(port)
        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), nodes[0].pubKey.hexStringToByteArray())
    }

    protected fun testListNodesWithProviderInternal(configFileName: String) {
        // Creating node0
        createNode(configFileName, 2)

        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
        val config = cliConf(clientConfigMap)
        val executor = cliExecution(config)
        executor.registerProvider(providerPublicKey)

        executor.enableProvider(providerPublicKey)
        val providerAuth = cliConf(provConfigMap)

        // Add node0 to managed blockchain
        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
        val auth = cliExecution(providerAuth)
        auth.addNode(node0, "127.0.0.1", 9870L)

        Thread.sleep(5000)

        val nodes = executor.listNodesWithProvider()
        val n = nodes[0].asDict()
        assertEquals("127.0.0.1", n["host"]!!.asString())
        assertEquals(9870L, n["port"]!!.asInteger())
        assertEquals(node0, n["pubkey"]!!.asByteArray().toHex().toLowerCase())

        assertEquals(providerPublicKey, n["provider"]!!.asByteArray().toHex())
        assertEquals(true, n["provider_active"]!!.asBoolean())
    }

    protected fun testAddNodeInternal(configFileName: String) {
        // Creating node0
        createNode(configFileName)
        val providerPublicKey =  provConfigMap.get("pubkey").toString()
//        "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
        val config = cliConf(clientConfigMap)
        val executor = cliExecution(config)
        executor.registerProvider(providerPublicKey)

        assertProviderRegistered(config, providerPublicKey)

        // provider active status should be true after calling enable
        executor.enableProvider(providerPublicKey)
        assertProviderEnabled(config, providerPublicKey)

        val providerAuth = cliConf(provConfigMap)
        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
        cliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
        assertAddedNode(config, providerPublicKey, host = "127.0.0.1", port = 9870L)
    }

}