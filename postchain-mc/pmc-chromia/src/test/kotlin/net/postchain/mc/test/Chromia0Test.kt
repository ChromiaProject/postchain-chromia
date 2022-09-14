package net.postchain.mc.test

/*
import assertk.assert
import assertk.assertions.contains
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.util.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.chromia0.CliExecutionC0
import net.postchain.mc.config.app.ClientConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Paths
import kotlin.test.*

*/

/*
class Chromia0Test() : ManagedModeTest() {

    override fun chainConfSnippet(): String {
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

    override val adminExecutor = CliExecutionC0(clientConfig)
    override val provExecutor = CliExecutionC0(provConfig)
    override val prov2Executor = CliExecutionC0(prov2Config)


    /*
    *  The pre-step includes starting a single
    * node, running the rell code in `rellSourceDir`. A majority of the tests starts with adding a first provider and
    * enabling it. These steps are therefore put in the pre-step.
    * For testing of these two operations (register_provider and enable_provider), a second provider is introduced.
    * */
    @BeforeEach
    fun setup() {
        val resourceDirectory = Paths.get("target", "chromia0", "rell")

        // start node and add the provider
        blockchain0ConfigGtv = run(runXmlFile(), resourceDirectory.toFile())
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(provConfig.pubKey))
        doAndBuildBlocks(clientConfig, adminExecutor.enableProviderInternal(provConfig.pubKey))
    }

    @Test
    fun testRegisterProvider() {
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
        assertProviderData(prov2Config.pubKey, "", false)
    }

    @Test
    fun testUpdateProviderName() {
        assertProviderData(provConfig.pubKey, name = "", isActive = true)
        val newName = "chromia"
        doAndBuildBlocks(provConfig, provExecutor.updateProviderInternal(provConfig.pubKey, newName, ""))
        assertProviderData(provConfig.pubKey, name = newName, isActive = true)
    }

    @Test
    fun testDisableEnableProvider() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // add second provider
        doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
        doAndBuildBlocks(clientConfig, adminExecutor.enableProviderInternal(prov2Config.pubKey))
        // provider active status should be true after calling enable
        assertProviderEnabled(prov2Config.pubKey)

        // The new provider adds node 1 and node 2
        addNode(prov2Config, node1Pubkey, node1Host, node1Port)
        addNode(prov2Config, node2Pubkey, node2Host, node2Port)

        // make node1 a signer of bc0
        doAndBuildBlocks(
            clientConfig, adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                node1Pubkey,
                -1L
            )
        )

        //make node2 a replica of bc0
        doAndBuildBlocks(prov2Config, prov2Executor.addReplicaInternal(clientConfig.brid, node2Pubkey))
        assertBlockchainReplica(clientConfig, node2Pubkey, node2Host, node2Port)

        //disable second provider
        doAndBuildBlocks(clientConfig, adminExecutor.disableProviderInternal(prov2Config.pubKey))
        // provider active status should be false after calling disable. Blockchain replicas list  and blockchain signers
        // list should also be updated accordingly.
        assertProviderDisabled(prov2Config.pubKey)
    }

    @Test
    fun testAddNode() {
        addNode0(provConfig)
    }

    @Test
    fun testAddBlockchain() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
    }

    @Test
    fun testAddBlockchainAcceptGtv() {
        // still need that for creating node test due to IntegrateTest expect xml to create node config
        // for testing only
        // Creating node0
        addNode0(provConfig)
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainInternal(bcConfigGtvFile, nodes[0].pubKey, "gtv"))
        awaitBlockchainReload()
        val listBlockchains = adminExecutor.listAllBlockchains()
        assertBcAdded(clientConfig, listBlockchains[0])
    }

    @Test
    fun testAddBlockchainXml() {
//        val configFileName = "/net/postchain/mc/test/config/blockchain_config.xml"
//        // Creating node0
        addNode0(provConfig)
//        val confFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources" + configFileName
        doAndBuildBlocks(clientConfig, adminExecutor.addBlockchainInternal(bcConfig1xmlFile, nodes[0].pubKey, "xml"))
        awaitBlockchainReload()

        val listBlockchains = adminExecutor.listAllBlockchains()
        assertBcAdded(clientConfig, listBlockchains[0])
    }

    @Test
    @Disabled //Ignoring, since a second test nod is not yet implemented.
    fun testAddBlockchainSigners() {
        // Creating node0
//        createNode(0, 2, NODE0_CONFIG_FILE, configFileName)
//

        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(
            clientConfig, adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                node1Pubkey,
                -1L
            )
        )
        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + registerProvider + enableProvider + 2*addNode + addBlockhain + addSigners + 5 = 10
        assertNextConfiguration(clientConfig, expectedHeight = 10L)

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
            assertProviderData(prov2Config.pubKey, "", false)
            true
        }
    }

    @Test
    fun testAddBlockchainSigners_Fail_DueToMissingNewSignerPeer() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & 2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        doAndBuildBlocks(
            clientConfig, adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                signers_list,
                -1L
            )
        )
        // Get next configuration height after adding new node as blockchain's signer
        // expected next congiguration height = -1 + registerProvider + enableProvider + 3*addNode + addBlockhain + addSigners + 5 = 11
        assertNextConfiguration(clientConfig, 11L)

        //Build blocks until new configuration is enabled
        buildAndAwaitBlocks(3)

        // Try to send tnx to api end point after the blockhain was re-configuration with new block signer
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(Duration.ONE_SECOND).until {
                doAndBuildBlocks(clientConfig, adminExecutor.registerProviderInternal(prov2Config.pubKey))
                true
            }
        }
    }

    @Test
    fun testRemoveNode() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        var nodeInfo = adminExecutor.getNodeInfo(node1Pubkey).asDict()
        assertTrue(nodeInfo["active"]!!.asBoolean())

        // Remove node1
        doAndBuildBlocks(clientConfig, provExecutor.removeNodeInternal(node1Pubkey))

        nodeInfo = adminExecutor.getNodeInfo(node1Pubkey).asDict()
        assertFalse(nodeInfo["active"]!!.asBoolean())
    }

    @Test
    fun testRemoveBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 & 2 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addNode(provConfig, node2Pubkey, node2Host, node2Port)

        // Add node1 as blockchain's signer
        val signers_list = "$node1Pubkey,$node2Pubkey"
        doAndBuildBlocks(
            clientConfig, adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                signers_list,
                -1L
            )
        )
        doAndBuildBlocks(clientConfig, adminExecutor.removeBlockchainSignersInternal(clientConfig.brid, signers_list))
        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(1, listBlockchainSigners.size)
    }


    @Test
    fun testAddConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(
            clientConfig, adminExecutor.addConfigurationInternal(
                clientConfig.brid, bcConfig1xmlFile,
                20L, "xml"
            )
        )
        assertNextConfiguration(clientConfig, 20L)
    }

    /*
    * 1.  A new configurations is added att height 20 (future height = 20)
    * 2.  A new signer is added at height 6 (current height = 6+5 = 11)
    *    The new config should not be applied at height 11.
    *    The new configuration should be updated with the new signer.
    * => initGtx == currentGTx
    *    current Signer list == future signer list
    *    currentGtx != future Gtx
    *    current config has two signers (init config has one signer)
    * */
    @Test
    fun testAddSignerToFutureConfig() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(
            clientConfig, adminExecutor.addConfigurationInternal(
                clientConfig.brid, bcConfig1xmlFile,
                20L, "xml"
            )
        )
        addNode(provConfig, node1Pubkey, node1Host, node1Port)
        addBcSigners(node1Pubkey)
        val futureConf = adminExecutor.getBlockchainConfiguration(clientConfig.brid, 20L)
        val signers = GtvDecoder.decodeGtv(futureConf).asDict()["signers"]
        assertNotNull(signers)
        val futureSignersArray = signers.asArray()
        assertEquals(2, futureSignersArray.size)

        //current is not really current, but current +5: h+5 = 6+5 = 11
        val currentConf = adminExecutor.getBlockchainConfiguration(clientConfig.brid, 11L)
        val currentDict = GtvDecoder.decodeGtv(currentConf).asDict()
        val currentSigners = currentDict["signers"]!!.asArray()
        val currentGtx = currentDict["gtx"]!!.asDict()
        assertEquals(2, currentSigners.size)

        val initConf = adminExecutor.getBlockchainConfiguration(clientConfig.brid, 0L)
        val initDict = GtvDecoder.decodeGtv(initConf).asDict()
        val initSigners = initDict["signers"]!!.asArray()
        val initGtx = initDict["gtx"]!!.asDict()
        assertEquals(1, initSigners.size)
        assertEquals(currentGtx, initGtx)

        assertTrue(
            currentSigners contentEquals futureSignersArray,
            "Future configurations are not updated with the new signer"
        )
        assertNotEquals(futureConf, currentConf, "future conf is applied too early")
        assertNotEquals(initConf, currentConf, "comparing with wrong current conf")
    }

    @Test
    fun testAddConfigurationAcceptGtv() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        doAndBuildBlocks(
            clientConfig, adminExecutor.addConfigurationInternal(
                clientConfig.brid, bcConfigGtvFile,
                20L, "gtv"
            )
        )
        assertNextConfiguration(clientConfig, 20L)
    }

    @Test
    fun testListBlockchainsForNode() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        var listBlockchains = provExecutor.listBlockchainsForNode(nodes[0].pubKey)
        assertEquals(1, listBlockchains.size)

        listBlockchains = provExecutor.listBlockchainsForNode(node1Pubkey)
        assertEquals(0, listBlockchains.size)
    }


    @Test
    fun testGetBlockchainConfiguration() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
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
    fun testGeBlockchainLastHeight() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        val h = provExecutor.getBlockchainLastHeight(clientConfig.brid)
        //Expected height = -1 + registerProvider + enableProvider + addNode + addBlockhain = 3
        assertEquals(3, h)
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
        val listBlockchains = adminExecutor.listAllBlockchains()
        assertEquals(1, listBlockchains.size)
    }

    @Test
    fun testListBlockchainReplicas() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // add node 1 as replica
        doAndBuildBlocks(provConfig, provExecutor.addReplicaInternal(clientConfig.brid, node1Pubkey), 5)
        assertBlockchainReplica(clientConfig, node1Pubkey, node1Host, node1Port)
    }

    @Test
    fun testListBlockchainSigners() {
        addNode0AndBc0(blockchain0ConfigGtv, clientConfig, provConfig)

        // Add node1 to managed blockchain
        addNode(provConfig, node1Pubkey, node1Host, node1Port)

        // Add node1 as blockchain's signer
        doAndBuildBlocks(
            clientConfig, adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                node1Pubkey,
                -1L
            )
        )
        val listBlockchainSigners = adminExecutor.listBlockchainSigners(clientConfig.brid)
        assertEquals(2, listBlockchainSigners.size)
        PrintUtils.printBlockchainNodes(listBlockchainSigners, false)
    }

    @Test
    fun testStopBlockchain() {
        initAndNode1Replica()

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
        PrintUtils.printProviders(providers)
    }


    @Test
    fun testGetProviderInfoErrorReporting() {
        addNode0(provConfig)
        val wrongProviderPublicKey = "03962AB49BC8D056C56A405DEFDA2448DE3A6AF65E6EA84019EE551A3526D0ADB1"
        assertThrows<CliError.Companion.CliException>("Query 'get_provider_data' failed: No records found") {
            provExecutor.getProviderInfo(wrongProviderPublicKey)
        }
    }

    @Test
    fun testGetNodeInfoErrorReporting() {
        addNode0(provConfig)
        val wrongNode = "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f58"
        assertThrows<CliError.Companion.CliException>("Query 'get_node_data' failed: No records found") {
            provExecutor.getNodeInfo(wrongNode)
        }
    }

    override fun cliExecution(cliConfig: ClientConfig): net.postchain.mc.cli.common0.CliExecution {
        return net.postchain.mc.cli.enterprise0.CliExecutionE0(cliConfig)
    }

    override fun addBc(blockchain0ConfigGtv: Gtv) {
        adminExecutor.sendTxUnconfirmed(adminExecutor.addBlockchainGtvInternal(blockchain0ConfigGtv, nodes[0].pubKey))
        buildAndAwaitBlocks(1)

    }

    override fun addBcSigners(nodeList: String) {
        adminExecutor.sendTxUnconfirmed(
            adminExecutor.addBlockchainSignersInternal(
                clientConfig.brid,
                nodeList,
                -1L
            )
        )
        buildAndAwaitBlocks(1)
    }

    //Test not needed. Functionality already tested in addNode0()
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

    //Test not needed. Functionality included in other tests e.g. assertProviderData
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

*/