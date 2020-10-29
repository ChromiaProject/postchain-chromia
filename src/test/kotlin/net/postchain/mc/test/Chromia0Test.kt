package net.postchain.mc.test

import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.chromia0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                                <bytea>${KeyPairHelper.pubKeyHex(node0BlockSignerKey)}</bytea>
                            </array>
                        </gtv> 
                    </config>
                </chain>
            </chains>
        """.trimIndent()
    }

//    lateinit var blockchain0ConfigGtv: Gtv
    //val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
    override val adminExecutor = CliExecution(clientConfig)
    override val provExecutor = CliExecution(provConfig)
    override val prov2Executor = CliExecution(prov2Config)


    /*
    * A majority of the tests starts with adding a first provider and enabling it. These steps are put in a pre-step.
    * When testing these two operations, a second provider is registered. The pre-step also includes starting a single
    * node, running the rell code in `rellSourceDir`.
    * */
    @Before
    fun setup() {
        // start node and add the provider
        blockchain0ConfigGtv = run("chroma0")
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(provConfig.pubKey))
        doAndBuildBlocks(clientConfig, adminExecutor.enableProviderInternal(provConfig.pubKey))
    }

    @Test
    fun testRegisterProvider() {
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
        assertProviderData(clientConfig, prov2Config.pubKey, "", false)
    }

    @Test
    fun testUpdateProviderName() {
        assertProviderData(clientConfig, provConfig.pubKey, name = "", isActive = true)
        val newName = "chromia"
        doAndBuildBlocks(provConfig, provExecutor.updateProviderInternal(provConfig.pubKey, newName, ""))
        assertProviderData(clientConfig, provConfig.pubKey, name = newName, isActive = true)
    }

    @Test
    fun testDisableEnableProvider() {
        // provider active status should be true after calling enable
        doAndBuildBlocks(clientConfig, adminExecutor.enableProviderInternal(provConfig.pubKey))
        assertProviderEnabled(clientConfig, provConfig.pubKey)

        // provider active status should be false after calling disable
        doAndBuildBlocks(clientConfig, adminExecutor.disableProviderInternal(provConfig.pubKey))
        assertProviderDisabled(clientConfig, provConfig.pubKey)
    }

    @Test
    fun testAddNode() {
        addNode0(provConfig)
    }

    @Test
    fun testAddBlockchain() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
    }

    @Test
    fun testAddBlockchainAcceptGtv() {
        // still need that for creating node test due to IntegrateTest expect xml to create node config
        // for testing only
        val configFileNameGtv = "/net/postchain/mc/test/config/0.gtv"
//        // Creating node0
        addNode0(provConfig)
        val confFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileNameGtv
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainInternal(confFile, nodes[0].pubKey, "gtv"))
        awaitBlockchainReload()

        val listBlockchains = adminExecutor.listAllBlockchains()
        assertBlockchainAdded(clientConfig, listBlockchains[0])
    }

    @Test
    fun testAddBlockchainSigners() {
        // Creating node0
//        createNode(0, 2, NODE0_CONFIG_FILE, configFileName)
//

        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainSignersInternal(clientConfig.brid, node1Pubkey))
        // Get next configuration height after adding new node as blockchain's signer
        // TODO: This is supposed to be 10, but a change in rell 0.10.3 causes it to be 9. We should fix our
        // module0 accordingly. See https://chromadev.zulipchat.com/#narrow/stream/144497-postchain-core-dev/topic/Chromia0/near/211956706
         assertNextConfiguration(clientConfig, expectedHeight = 9L)

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
//        for ()
//        buildBlockAndCommit(nodes[0])

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(3)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        Awaitility.await().atMost(Duration.ONE_SECOND).until {
            doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
            assertProviderData(clientConfig, prov2Config.pubKey, "", false)
            true
        }
        }

    @Test(expected = org.awaitility.core.ConditionTimeoutException::class)
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)

//        // Creating node0
//        createNode(0, 2, NODE0_CONFIG_FILE, configFileName)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainSignersInternal(clientConfig.brid, node1Pubkey))

        assertNextConfiguration(clientConfig, 9L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(3)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        Awaitility.await().atMost(Duration.ONE_SECOND).until {
            doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
            true
        }
    }

    @Test
    fun testAddConfiguration() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
        doAndBuildBlocks(clientConfig, adminExecutor.addConfigurationInternal(clientConfig.brid, blockchainConfigFile,
                20L, "xml"),5)
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testAddConfigurationAcceptGtv() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/0.gtv"
        doAndBuildBlocks(clientConfig, adminExecutor.addConfigurationInternal(clientConfig.brid, blockchainConfigFile,
                20L, "gtv"),5)
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testListBlockchainsForNode() {
        addNode0AndBlockchain0(blockchain0ConfigGtv, clientConfig, provConfig)
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
        addNode(provConfig,node1Pubkey, node1Host, node1Port)

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
        doAndBuildBlocks(clientConfig,adminExecutor.addBlockchainSignersInternal(clientConfig.brid, node1Pubkey))
        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
    }

    @Test
    fun testStopBlockchain() {
        initAndNode1ReplicaAndSigner()

        doAndBuildBlocks(clientConfig, adminExecutor.stopBlockchainInternal(clientConfig.brid, true))
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
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        assertListNodesByProvider()
    }

    @Test
    fun testListProviders() {
        addNode0(provConfig)

//        add a second provider
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))

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