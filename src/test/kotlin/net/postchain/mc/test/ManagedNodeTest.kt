package net.postchain.mc.test

import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.client.DefaultSigner
import net.postchain.client.PostchainClient
import net.postchain.client.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.*
import net.postchain.mc.cli.CliError
import net.postchain.mc.cli.CliExecution
import net.postchain.mc.config.app.AppConfig
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

const val DEFAULT_APP_CONFIG = "app.properties"
const val adminPrivKey = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
const val adminPubKey = "02487d53cc19d75b12296fd3873ee2f65bdb8e9459cd2ee9bdabd3fc45cb3e87a9"
const val pubkey = "0395f16c8024ba14c6fd99f26ec4f78137e9419e836492bea087ec782f6b44170d"
const val privkey = "2d439640c141d8aed2dbc97aec315b58c416fe3301ad47e0d14a5809ff922d2a"
const val newPeerPubKey = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
const val newBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4"
const val systemBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"

class ManagedNodeTest : IntegrationTest() {

    private val postchainClientFactory = PostchainClientFactory()

    private var data: Map<String, Gtv> = mapOf()

    private var peers: Array<out Gtv> = arrayOf()

    private var exception: Exception? = null

    private var version: Long? = null

    private var blockchain: Gtv? = null

    private var height: Long? = null

    private var blockchainRID: ByteArray? = null

    private fun getAdminSigner(): Pair<ByteArray, ByteArray> {
        return Pair(adminPubKey.hexStringToByteArray(), adminPrivKey.hexStringToByteArray())
    }

    private fun getSigner(): Pair<ByteArray, ByteArray> {
        return Pair(pubkey.hexStringToByteArray(), privkey.hexStringToByteArray())
    }

    private fun getPostchainClient(configFile: String): PostchainClient {
        val config = AppConfig.fromPropertiesFile(configFile)

        val resolver = postchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.adminPubKey.hexStringToByteArray(), config.adminPrivKey.hexStringToByteArray())
        return postchainClientFactory.getClient(resolver, config.brid.hexStringToByteArray(), DefaultSigner(sigMaker, config.adminPubKey.hexStringToByteArray()))
    }

    private fun createPostchainTestNodes(nodesCount: Int, configFileName: String): Array<PostchainTestNode> {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        configOverrides.setProperty("api.port", 7740L)
        return createNodes(nodesCount, configFileName)
    }

    @Before
    fun setup() {
        data = mapOf()
        peers = arrayOf()
        exception = null
        version = null
        blockchain = null
        height = null
        blockchainRID = null
    }

    @Test
    fun testAddPeer() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_peer_infos", GtvDictionary.build(mapOf())).success {
            data = it.asArray()[0].asDict()
        }.fail {
            exception = it
        }

        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(data["host"]?.asString()).isEqualTo("127.0.0.1")
            assertk.assert(data["port"]?.asBigInteger()?.toLong()).isEqualTo(9090L)
            assertArrayEquals(data["pubkey"]?.asByteArray(), newPeerPubKey.hexStringToByteArray())
            assertk.assert(exception).isNull()
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvDictionary.build(mapOf())).success {
            version = it.asInteger()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(version).isEqualTo(1L)
        }
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_SignerIsNotAdmin() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app_1.properties", "127.0.0.1", 9090L, newPeerPubKey, getSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_SignerNotFound() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_SignerNotFound2() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app_1.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_ConfigNotFound() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app_2.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_EmptyConfigFile() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app_empty.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddPeer_ConfigFileMissingKeys() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app_missing.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
    }

    @Test
    fun testRemovePeer() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_peer_infos", GtvDictionary.build(mapOf())).success {
            data = it.asArray()[0].asDict()
        }.fail {
            exception = it
        }

        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(data["host"]?.asString()).isEqualTo("127.0.0.1")
            assertk.assert(data["port"]?.asBigInteger()?.toLong()).isEqualTo(9090L)
            assertArrayEquals(data["pubkey"]?.asByteArray(), newPeerPubKey.hexStringToByteArray())
            assertk.assert(exception).isNull()
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvDictionary.build(mapOf())).success {
            version = it.asInteger()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(version).isEqualTo(1L)
        }

        CliExecution().removePeer(DEFAULT_APP_CONFIG, newPeerPubKey, getAdminSigner())
        client.query("nm_get_peer_infos", GtvDictionary.build(mapOf())).success {
            peers = it.asArray()
        }.fail {
            exception = it
        }

        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(peers.isEmpty()).isTrue()
            assertk.assert(exception).isNull()
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvDictionary.build(mapOf())).success {
            version = it.asInteger()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(version).isEqualTo(2L)
        }
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer1_SignerIsNotAdmin() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        CliExecution().removePeer("app_1.properties", newPeerPubKey, getSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer_SignerNotFound() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        CliExecution().removePeer(DEFAULT_APP_CONFIG, newPeerPubKey, getSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer_SignerNotFound2() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        CliExecution().removePeer("app_1.properties", newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer_ConfigNotFound() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        CliExecution().removePeer("app_2.properties", newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer_EmptyConfigFile() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().removePeer("app_empty.properties", newPeerPubKey, getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testRemovePeer_ConfigFileMissingKeys() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().removePeer("app_missing.properties", newPeerPubKey, getAdminSigner())
    }

    @Test
    fun testAddBlockchainConfiguration() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_blockchain_configuration",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(0L))).success {
            blockchain = it
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(blockchain).isNotNull()
            val modules = GtvFactory.decodeGtv(blockchain?.asByteArray()!!).asDict()["gtx"]?.get("modules")
            assertk.assert(modules?.get(0)?.asString()).isEqualTo("net.postchain.configurations.GTXTestModule")
            assertk.assert(exception).isNull()
        }

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(-1L))).success {
            height = it.asInteger()
        }.fail {
            exception = it
        }

        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(height).isEqualTo(0L)
        }

        client.query("nm_compute_blockchain_list", GtvFactory.gtv("node_id" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()))).success {
            blockchainRID = it.asArray()[0].asByteArray()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertArrayEquals(newBlockchainRID.hexStringToByteArray(), blockchainRID)
        }
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddBlockchainConfiguration_ConfigNotFound() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration("app_2.properties", newBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddBlockchainConfiguration_EmptyConfigFile() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration("app_empty.properties", newBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())
    }

    @Test(expected = CliError.Companion.CliException::class)
    fun testAddBlockchainConfiguration_ConfigFileMissingKeys() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration("app_missing.properties", newBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())
    }

    @Test
    fun testUpdateBlockchainConfiguration() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_blockchain_configuration",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(0L))).success {
            blockchain = it
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(blockchain).isNotNull()
            val modules = GtvFactory.decodeGtv(blockchain?.asByteArray()!!).asDict()["gtx"]?.get("modules")
            assertk.assert(modules?.get(0)?.asString()).isEqualTo("net.postchain.configurations.GTXTestModule")
            assertk.assert(exception).isNull()
        }

        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 10L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(8L))).success {
            height = it.asInteger()
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(height).isEqualTo(10L)
            assertk.assert(exception).isNull()
        }

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(10L))).success {
            blockchain = it
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(blockchain).isEqualTo(GtvNull)
            assertk.assert(exception).isNull()
        }

        client.query("nm_compute_blockchain_list", GtvFactory.gtv("node_id" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()))).success {
            blockchainRID = it.asArray()[0].asByteArray()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertArrayEquals(newBlockchainRID.hexStringToByteArray(), blockchainRID)
        }
    }

    @Test
    fun testSystemPeer() {
        // Add blockchain configuration
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, systemBlockchainRID, 0L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_blockchain_configuration",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(systemBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(0L))).success {
            blockchain = it
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(blockchain).isNotNull()
            val modules = GtvFactory.decodeGtv(blockchain?.asByteArray()!!).asDict()["gtx"]?.get("modules")
            assertk.assert(modules?.get(0)?.asString()).isEqualTo("net.postchain.configurations.GTXTestModule")
            assertk.assert(exception).isNull()
        }

        // Add peer info
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
        client.query("nm_get_peer_infos", GtvDictionary.build(mapOf())).success {
            data = it.asArray()[0].asDict()
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(data["host"]?.asString()).isEqualTo("127.0.0.1")
            assertk.assert(data["port"]?.asBigInteger()?.toLong()).isEqualTo(9090L)
            assertArrayEquals(data["pubkey"]?.asByteArray(), newPeerPubKey.hexStringToByteArray())
            assertk.assert(exception).isNull()
        }

        // Add system peer, a.k.a update blockchain's signer
        CliExecution().addSystemPeer(DEFAULT_APP_CONFIG, newPeerPubKey, getAdminSigner())
        client.query("nm_compute_blockchain_list", GtvFactory.gtv("node_id" to GtvFactory.gtv(systemBlockchainRID.hexStringToByteArray()))).success {
            blockchainRID = it.asArray()[0].asByteArray()
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertArrayEquals(systemBlockchainRID.hexStringToByteArray(), blockchainRID)
        }

        // check next configuration height for update one
        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(systemBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(4L))).success {
            height = it.asInteger()
        }.fail {
            exception = it
        }
        Awaitility.await().atMost(Duration.TWO_SECONDS).untilAsserted {
            assertk.assert(height!! > 5L).isTrue()
            assertk.assert(exception).isNull()
        }
    }
}