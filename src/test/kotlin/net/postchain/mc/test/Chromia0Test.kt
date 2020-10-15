package net.postchain.mc.test

import assertk.assertions.isGreaterThan
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.chromia0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

//const val NODE0_CONFIG_FILE = "node0.properties"
//const val NODE1_CONFIG_FILE = "node1.properties"
//const val adminPrivKey = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
//const val adminPubKey = "02487d53cc19d75b12296fd3873ee2f65bdb8e9459cd2ee9bdabd3fc45cb3e87a9"
//const val pubkey = "0395f16c8024ba14c6fd99f26ec4f78137e9419e836492bea087ec782f6b44170d"
//const val privkey = "2d439640c141d8aed2dbc97aec315b58c416fe3301ad47e0d14a5809ff922d2a"
//const val newPeerPubKey = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
//const val newBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4"
//const val systemBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"

class Chromia0Test() : ManagedModeTest() {

    override fun chainConfSnippet(): String{
        val module = "chroma0"

        return """
            <chains>
                <chain name="manager" iid="0">
                    <config height="0" add-dependencies="false">
                        <app module="${module}">
                            <args module="${module}">
                                <arg key="admin"><bytea>${KeyPairHelper.pubKeyHex(adminKey)}</bytea></arg>
                            </args>
                        </app>
                        <gtv path="signers">
                            <array>
                                <bytea>${KeyPairHelper.pubKeyHex(blockSignerKeyNode0)}</bytea>
                            </array>
                        </gtv>
                    </config>
                </chain>
            </chains>
        """.trimIndent()
    }

    val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
    override val adminExecutor = CliExecution(clientConfig)
    override val provExecutor = CliExecution(provConfig)
    override val prov2Executor = CliExecution(prov2Config)

    @Before
    fun setup() {
        // start node and add the provider
        run("chroma0")
        adminExecutor.registerProvider(provConfig.pubKey)
        adminExecutor.enableProvider(provConfig.pubKey)
    }

    @Test
    fun testRegisterProvider() {
        adminExecutor.registerProvider(prov2Config.pubKey)
        assertProviderData(clientConfig, prov2Config.pubKey, "")
        awaitBlockchainReload()
    }

    @Test
    fun testUpdateProviderName() {
        assertProviderData(clientConfig, provConfig.pubKey, name = "")
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        // Creating node0
//        createNode(configFileName)
//
//        var config = cliConf(clientConfigMap)
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        CliExecution(config).registerProvider(providerPublicKey)
//
//        val client = getPostchainClient(config)
//        var provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        var data = provider.asDict()
//        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        assertk.assert(data["name"]?.asString()).isEqualTo("")
//        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
//        println(data["beneficiary"]?.asByteArray()?.toHex())
//
//        config = cliConf(provConfigMap)
        val newName = "chromia"
        adminExecutor.updateProvider(provConfig.pubKey, newName, "")

        assertProviderData(clientConfig, provConfig.pubKey, name = newName)
//        val client = getPostchainClient(clientConfig)
//        provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        data = provider.asDict()
//        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        assertk.assert(data["name"]?.asString()).isEqualTo("chromia")
    }

    @Test
    fun testDisableEnableProvider() {
        // provider active status should be true after calling enable
        adminExecutor.enableProvider(provConfig.pubKey)
        assertProviderEnabled(clientConfig, provConfig.pubKey)
        awaitBlockchainReload()

        // provider active status should be false after calling disable
        adminExecutor.disableProvider(provConfig.pubKey)
        assertProviderDisabled(clientConfig, provConfig.pubKey)

    }

    @Test
    fun testAddNode() {
//        testAddNodeInternal(configFileName)
        addNode0(provConfig)
    }

    @Test
    fun testAddBlockchain() {
        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
    }

    @Test
    fun testAddBlockchainAcceptGtv() {
        // still need that for creating node test due to IntegrateTest expect xml to create node config
        // for testing only
//        val configFileNameXml = "/net/postchain/mc/test/config/blockchain_config.xml"

        val configFileNameGtv = "/net/postchain/mc/test/config/0.gtv"
//        // Creating node0
//        createNode(configFileNameXml)
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        val client = getPostchainClient(cliConf(clientConfigMap))
//
//        // provider active status should be true after calling enable
//        executor.enableProvider(providerPublicKey)
//
//        val providerAuth = cliConf(provConfigMap)
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
        addNode0(clientConfig)

//        Thread.sleep(5000)
        adminExecutor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
                + "/src/test/resources" + configFileNameGtv, nodes[0].pubKey, "gtv")
        assertBlockchainAdded(clientConfig)
//        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
//                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()
//        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
    }

    @Test
    fun testAddBlockchainSigners() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        val providerAuth = cliConf(provConfigMap)
//        val config = cliConf(clientConfigMap)
//        val auth = CliExecution(providerAuth)
        // Creating node0
//        createNode(0, 2, NODE0_CONFIG_FILE, configFileName)
//
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        val client = getPostchainClient(config)
//        var provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        var data = provider.asDict()
//        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        assertk.assert(data["name"]?.asString()).isEqualTo("")
//        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
//
//        executor.enableProvider(providerPublicKey)
//        provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        data = provider.asDict()
//        assertk.assert(data["active"]?.asBoolean()).isEqualTo(true)
//
//        val providerAuth = cliConf(provConfigMap)
//
//        // Add node0 to managed blockchain
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        val auth = CliExecution(providerAuth)
//        auth.addNode(node0, "127.0.0.1", 9870L)
//        var node = client.query("get_node_data", GtvFactory.gtv(
//                "pubkey" to GtvFactory.gtv(node0.hexStringToByteArray()))).get().asDict()
//        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
//        assertk.assert(node["host"]?.asString()).isEqualTo("127.0.0.1")
//        assertk.assert(node["port"]?.asInteger()).isEqualTo(9870L)
//        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), node0.hexStringToByteArray())
//
//        Thread.sleep(5000)
//
//        // Add blockchain config for self-awareness
//        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, node0, "xml")
//        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
//                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()
//        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
//        Thread.sleep(5000)

        addNode0AndBlockchain(configFileName, clientConfig, provConfig)

        // Add node1 to managed blockchain
        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
        assertAddedNode(clientConfig, provConfig.pubKey, node1Host, node1Port)
//        val node = client.query("get_node_data", GtvFactory.gtv(
//                "pubkey" to GtvFactory.gtv(node1.hexStringToByteArray()))).get().asDict()
//        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
//        assertk.assert(node["host"]?.asString()).isEqualTo("127.0.0.1")
//        assertk.assert(node["port"]?.asInteger()).isEqualTo(9871L)
//        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerAuth.pubKey.hexStringToByteArray())
//        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), node1.hexStringToByteArray())
//        Thread.sleep(5000)

        // Add node1 as blockchain's signer
        adminExecutor.addBlockchainSigners(clientConfig.brid, node1Pubkey)
        // Get next configuration height after adding new node as blockchain's signer
        // TODO: This is supposed to be 10, but a change in rell 0.10.3 causes it to be 9. We should fix our
        // module0 accordingly. See https://chromadev.zulipchat.com/#narrow/stream/144497-postchain-core-dev/topic/Chromia0/near/211956706
         assertAddConfiguration(clientConfig, expectedHeight = 9L)
//        val height = client.query("nm_find_next_configuration_height", GtvFactory.gtv(
//                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to GtvFactory.gtv(0L))).get()
//
//        assertk.assert(height.asInteger()).isEqualTo(9L)
//
//        // Get next configuration
//        val bc = client.query("nm_get_blockchain_configuration", GtvFactory.gtv(
//                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to height)).get()
//        assertk.assert(bc.asByteArray()).isNotNull()
//
//        Thread.sleep(120000)

//        // Creating node1
//        createSingleNode(1, 2, NODE1_CONFIG_FILE, configFileName) { appConfig, n ->
//            runStorageCommand(appConfig) {
//                val databaseAccess = DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
//                databaseAccess.addPeerInfo(it, TestPeerInfos.peerInfo0)
//                databaseAccess.addPeerInfo(it, TestPeerInfos.peerInfo1)
//            }
//        }
//        Thread.sleep(50000)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
//        val anotherProviderPR = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
        adminExecutor.registerProvider(prov2Config.pubKey)
        assertProviderData(clientConfig, prov2Config.pubKey, "")
        }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//
//        // Creating node0
//        createNode(0, 2, NODE0_CONFIG_FILE, configFileName)
//
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        val client = getPostchainClient(cliConf(clientConfigMap))
//        var provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        var data = provider.asDict()
//        Assert.assertArrayEquals(data["pubkey"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        assertk.assert(data["name"]?.asString()).isEqualTo("")
//        assertk.assert(data["active"]?.asBoolean()).isEqualTo(false)
//
//        executor.enableProvider(providerPublicKey)
//        provider = client.query("get_provider_data", GtvFactory.gtv("pubkey" to GtvFactory.gtv(providerPublicKey.hexStringToByteArray()))).get()
//        data = provider.asDict()
//        assertk.assert(data["active"]?.asBoolean()).isEqualTo(true)
//
//        val providerAuth = cliConf(provConfigMap)
//
//        // Add node0 to managed blockchain
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
//        var node = client.query("get_node_data", GtvFactory.gtv(
//                "pubkey" to GtvFactory.gtv(node0.hexStringToByteArray()))).get().asDict()
//        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
//        assertk.assert(node["host"]?.asString()).isEqualTo("127.0.0.1")
//        assertk.assert(node["port"]?.asInteger()).isEqualTo(9870L)
//        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), node0.hexStringToByteArray())
//
//        Thread.sleep(5000)
//
//        // Add blockchain config for self-awareness
//        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, node0, "xml")
//        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
//                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()
//        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)
        Thread.sleep(5000)

        // Add node1 to managed blockchain
        provExecutor.addNode(node1Pubkey, node1Host, node1Port)
        assertAddedNode(clientConfig, provConfig.pubKey, node1Host, node1Port)
//        node = client.query("get_node_data", GtvFactory.gtv(
//                "pubkey" to GtvFactory.gtv(node1.hexStringToByteArray()))).get().asDict()
//        assertk.assert(node["active"]?.asBoolean()).isEqualTo(true)
//        assertk.assert(node["host"]?.asString()).isEqualTo("127.0.0.1")
//        assertk.assert(node["port"]?.asInteger()).isEqualTo(9871L)
//        Assert.assertArrayEquals(node["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
//        Assert.assertArrayEquals(node["pubkey"]?.asByteArray(), node1.hexStringToByteArray())
        Thread.sleep(5000)

        // Add node1 as blockchain's signer
        adminExecutor.addBlockchainSigners(clientConfig.brid, node1Pubkey)

//        // Get next configuration height after adding new node as blockchain's signer
//        val height = client.query("nm_find_next_configuration_height", GtvFactory.gtv(
//                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to GtvFactory.gtv(0L))).get()
//        assertk.assert(height.asInteger()).isEqualTo(9L)
//
//        // Get next configuration
//        val bc = client.query("nm_get_blockchain_configuration", GtvFactory.gtv(
//                "blockchain_rid" to GtvFactory.gtv(config.brid), "height" to height)).get()
//        assertk.assert(bc.asByteArray()).isNotNull()
        assertAddConfiguration(clientConfig, 20L)

        Thread.sleep(120000)

        // Try to send tnx to api end point after the blocchain was re-configuration with new block signer
//        val anotherProviderPR = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
        adminExecutor.registerProvider(prov2Config.pubKey)
    }

    @Test
    fun testAddConfiguration() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        val clientConfig = cliConf(clientConfigMap)
//        val provConfig = cliConf(provConfigMap)

        addNode0AndBlockchain(configFileName, clientConfig, provConfig)

        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
        adminExecutor.addConfiguration(clientConfig.brid, blockchainConfigFile, 20L, "xml")
        assertAddConfiguration(clientConfig, 20L)
    }

    @Test
    fun testAddConfigurationAcceptGtv() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        // Creating node0
//        createNode(configFileName)
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//        executor.enableProvider(providerPublicKey)
//        val client = getPostchainClient(cliConf(clientConfigMap))
//        val providerAuth = cliConf(provConfigMap)
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
//
//        Thread.sleep(5000)
//        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, node0, "xml")

        addNode0AndBlockchain(configFileName, clientConfig, provConfig)

        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/0.gtv"
        adminExecutor.addConfiguration(clientConfig.brid, blockchainConfigFile, 20L, "gtv")

        assertAddConfiguration(clientConfig, 20L)
    }

    @Test
    fun testListBlockchainsForNode() {

//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        // Creating node0
//        createNode(configFileName)
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        val client = getPostchainClient(cliConf(clientConfigMap))
//
//        // provider active status should be true after calling enable
//        executor.enableProvider(providerPublicKey)
//
//        val providerAuth = cliConf(provConfigMap)
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
//
//        Thread.sleep(5000)
//        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, node0, "xml")
//        val blockchain = client.query("get_blockchain", GtvFactory.gtv(
//                "rid" to GtvFactory.gtv(config.brid.hexStringToByteArray()))).get()
//        assertk.assert(blockchain.asInteger()).isGreaterThan(0L)

        addNode0(provConfig)
//        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
//        executor.addConfiguration(config.brid, blockchainConfigFile, 20L, "xml")

        val listBlockchains = adminExecutor.listBlockchainsForNode(nodes[0].pubKey)

        assertEquals(1, listBlockchains.size)
    }

    //Test not needed. Functionality alread tested in addNode0()
    @Test
    fun testGetNodeInfo() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        // Creating node0
//        createNode(configFileName)
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        // provider active status should be true after calling enable
//        executor.enableProvider(providerPublicKey)
//
//        val providerAuth = cliConf(provConfigMap)
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
//        val node = adminExecutor.getNodeInfo(node0).asDict()
//        assertEquals(true, node["active"]!!.asBoolean())
//        assertEquals("127.0.0.1", node["host"]!!.asString())
//        assertEquals(9870L, node["port"]!!.asInteger())
//        Assert.assertArrayEquals(node["provider"]!!.asByteArray(), providerPublicKey.hexStringToByteArray())
//        Assert.assertArrayEquals(node["pubkey"]!!.asByteArray(), node0.hexStringToByteArray())
    }

    //Test not needed. Functionality included in other tests
    @Test
    fun testGetProviderInfo() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        createNode(configFileName)
//
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//        executor.enableProvider(providerPublicKey)
//        val data = executor.getProviderInfo(providerPublicKey).asDict()
//        Assert.assertArrayEquals(data["pubkey"]!!.asByteArray(), providerPublicKey.hexStringToByteArray())
//        assertEquals("", data["name"]!!.asString())
//        assertEquals(true, data["active"]!!.asBoolean())
    }

    @Test
    fun testGetBlockchainConfiguration() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        createNode(configFileName)
//
//        val providerPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB0"
//        val config = cliConf(clientConfigMap)
//        val executor = CliExecution(config)
//        executor.registerProvider(providerPublicKey)
//
//        // provider active status should be true after calling enable
//        executor.enableProvider(providerPublicKey)
//
//        val providerAuth = cliConf(provConfigMap)
//        val node0 = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
//        CliExecution(providerAuth).addNode(node0, "127.0.0.1", 9870L)
//
//        Thread.sleep(5000)
//        executor.addBlockchain(Paths.get(".").toAbsolutePath().normalize().toString()
//                + "/src/test/resources" + configFileName, node0, "xml")

        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
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
        val node1 = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
        provExecutor.addNode(node1, "127.0.0.1", 9871L)
        Thread.sleep(5000)

        val nodeList = provExecutor.listNodes()
        val n0 = nodeList.get(0)
        assertEquals("127.0.0.1", n0.get(0).asString())
        assertEquals(9870L, n0.get(1).asInteger())
        assertEquals(nodes[0].pubKey,  n0.get(2).asByteArray().toHex())

        val n1 = nodeList.get(1)
        assertEquals("127.0.0.1", n1.get(0).asString())
        assertEquals(9871L, n1.get(1).asInteger())
        assertEquals(node1,  n1.get(2).asByteArray().toHex().toLowerCase())

    }

    @Test
    fun testListNodesWithProvider() {
        addNode0(provConfig)
        assertListNodes(provConfig)
    }

    @Test
    fun testListBlockchains() {
        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
        val listBlockchains = adminExecutor.listAllBlockchains()
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainReplicas() {
        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
        val node1 = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
        provExecutor.addNode(node1, "127.0.0.1", 9871L)
        Thread.sleep(5000)

        // add node 1 as replica
        provExecutor.addReplica(clientConfig.brid, node1)

        val listBlockchains = provExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(1, listBlockchains.size)
        val bc = listBlockchains.get(0).asArray()
        assertEquals(clientConfig.brid, bc.get(0).asByteArray().toHex())
        assertEquals(node1.toUpperCase(), bc.get(1).asByteArray().toHex())
        assertEquals("127.0.0.1", bc.get(2).asString())
        assertEquals(9871L, bc.get(3).asInteger())
        assertTrue(bc.get(4).asBoolean())
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBlockchain(configFileName, clientConfig, provConfig)
        Thread.sleep(5000)

        // Add node1 to managed blockchain
        val node1 = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
        provExecutor.addNode(node1, "127.0.0.1", 9871L)
        Thread.sleep(5000)

        // Add node1 as blockchain's signer
        adminExecutor.addBlockchainSigners(clientConfig.brid, node1)

        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {

        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"

//        val adminConfig = cliConf(clientConfigMap)
//        val executor = CliExecution(adminConfig)
//        var replicas = executor.listBlockchainReplicas(adminConfig.brid)
//        var listBlockchainSigners = executor.listBlockchainSigners(adminConfig.brid)
        initAndNode1ReplicaAndSigner(configFileName)

        adminExecutor.stopBlockchain(clientConfig.brid, true)

        // query replicas again to ensure it was deleted after stop blockchain
        val replicas = adminExecutor.listBlockchainReplicas(clientConfig.brid)
        assertEquals(0, replicas.size)

        // query signers again to ensure it was deleted after stop blockchain
        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(0, listBlockchainSigners.size)

    }

    @Test
    fun testListNodesByProvider() {
        addNode0(provConfig)

        // Add node1 to managed blockchain
        val node1 = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
        provExecutor.addNode(node1, "127.0.0.1", 9871L)
        Thread.sleep(5000)

        val nodeList = provExecutor.listNodesByProvider(provConfig.pubKey)
        assertEquals(2, nodeList.size)
        assertEquals(nodes[0].pubKey.toUpperCase(), nodeList[0].asDict()["pubkey"]!!.asByteArray().toHex())
        assertEquals(node1.toUpperCase(), nodeList[1].asDict()["pubkey"]!!.asByteArray().toHex())
    }

    @Test
    fun testListProviders() {
        addNode0(provConfig)

//        add a second provider
        adminExecutor.registerProvider(prov2Config.pubKey)

        val providers = adminExecutor.listProviders()

        assertEquals(2, providers.size)

        val provider1 = providers.get(0).asDict()
        assertEquals(provConfig.pubKey, provider1["pubkey"]!!.asByteArray().toHex())
        assertEquals(true, provider1["active"]!!.asBoolean())

        val provider2 = providers.get(1).asDict()
        assertEquals(prov2Config.pubKey, provider2["pubkey"]!!.asByteArray().toHex())
        assertEquals(false, provider2["active"]!!.asBoolean())
    }

    override fun cliExecution(cliConfig: ClientConfig): net.postchain.mc.cli.common0.CliExecution {
        return net.postchain.mc.cli.enterprise0.CliExecution(cliConfig)
    }
//
//    @Test(expected = CliError.Companion.CliException::class)
//    fun testAddBlockchainConfiguration_ConfigNotFound() {
//        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
//        CliExecution().addBlockchainConfiguration("app_2.properties", newBlockchainRID, 0L,
//                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
//                getAdminSigner())
//    }
//
//    @Test(expected = CliError.Companion.CliException::class)
//    fun testAddBlockchainConfiguration_EmptyConfigFile() {
//        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
//        CliExecution().addBlockchainConfiguration("app_empty.properties", newBlockchainRID, 0L,
//                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
//                getAdminSigner())
//    }
//
//    @Test(expected = CliError.Companion.CliException::class)
//    fun testAddBlockchainConfiguration_ConfigFileMissingKeys() {
//        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
//        CliExecution().addBlockchainConfiguration("app_missing.properties", newBlockchainRID, 0L,
//                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
//                getAdminSigner())
//    }
//
//    @Test
//    fun testUpdateBlockchainConfiguration() {
//        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
//        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 0L,
//                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
//                getAdminSigner())
//
//        val client = getPostchainClient(DEFAULT_APP_CONFIG)
//        client.query("nm_get_blockchain_configuration",
//                GtvFactory.gtv(
//                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
//                        "height" to GtvFactory.gtv(0L))).success {
//            blockchain = it
//        }.fail {
//            exception = it
//        }
//        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
//            assertk.assert(blockchain).isNotNull()
//            val modules = GtvFactory.decodeGtv(blockchain?.asByteArray()!!).asDict()["gtx"]?.get("modules")
//            assertk.assert(modules?.get(0)?.asString()).isEqualTo("net.postchain.configurations.GTXTestModule")
//            assertk.assert(exception).isNull()
//        }
//
//        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 10L,
//                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
//                getAdminSigner())
//
//        client.query("nm_find_next_configuration_height",
//                GtvFactory.gtv(
//                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
//                        "height" to GtvFactory.gtv(8L))).success {
//            height = it.asInteger()
//        }.fail {
//            exception = it
//        }
//        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
//            assertk.assert(height).isEqualTo(10L)
//            assertk.assert(exception).isNull()
//        }
//
//        client.query("nm_find_next_configuration_height",
//                GtvFactory.gtv(
//                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
//                        "height" to GtvFactory.gtv(10L))).success {
//            blockchain = it
//        }.fail {
//            exception = it
//        }
//        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
//            assertk.assert(blockchain).isEqualTo(GtvNull)
//            assertk.assert(exception).isNull()
//        }
//
//        client.query("nm_compute_blockchain_list", GtvFactory.gtv("node_id" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()))).success {
//            blockchainRID = it.asArray()[0].asByteArray()
//        }
//        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
//            assertArrayEquals(newBlockchainRID.hexStringToByteArray(), blockchainRID)
//        }
//    }
}