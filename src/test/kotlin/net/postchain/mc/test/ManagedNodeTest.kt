package net.postchain.mc.test

import assertk.fail
import net.postchain.client.DefaultSigner
import net.postchain.client.PostchainClient
import net.postchain.client.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.CliExecution
import net.postchain.mc.config.app.AppConfig
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.nio.file.Paths

const val DEFAULT_APP_CONFIG = "app.properties"
const val adminPrivKey = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
const val adminPubKey = "02487d53cc19d75b12296fd3873ee2f65bdb8e9459cd2ee9bdabd3fc45cb3e87a9"
const val newPeerPubKey = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"
const val newBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4"

class ManagedNodeTest : IntegrationTest() {

    private val postchainClientFactory = PostchainClientFactory()

    private fun getAdminSigner(): Pair<ByteArray, ByteArray> {
        return Pair(adminPubKey.hexStringToByteArray(), adminPrivKey.hexStringToByteArray())
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

    @Test
    fun testAddPeer() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos"))).success {
            val resp = it.asArray()[0].asDict()
            assertEquals("127.0.0.1", resp["host"]?.asString())
            assertEquals(9090L, resp["port"]?.asBigInteger()?.toLong())
            assertArrayEquals(newPeerPubKey.hexStringToByteArray(), resp["pubkey"]?.asByteArray())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).success {
            assertEquals(1L, it.asInteger())
        }
    }

    @Test
    fun testRemovePeer() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer(DEFAULT_APP_CONFIG, "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        val client = getPostchainClient(DEFAULT_APP_CONFIG)
        client.query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos"))).success {
            val resp = it.asArray()[0].asDict()
            assertEquals("127.0.0.1", resp["host"]?.asString())
            assertEquals(9090L, resp["port"]?.asBigInteger()?.toLong())
            assertArrayEquals(newPeerPubKey.hexStringToByteArray(), resp["pubkey"]?.asByteArray())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).success {
            assertEquals(1L, it.asInteger())
        }

        CliExecution().removePeer(DEFAULT_APP_CONFIG, newPeerPubKey, getAdminSigner())
        client.query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos"))).success {
            assertTrue(it.asArray().isEmpty())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }

        // check peer list version also
        client.query("nm_get_peer_list_version", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_list_version"))).success {
            assertEquals(2L, it.asInteger())
        }
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
            assertTrue(!it.isNull())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(-1L))).success {
            assertTrue(!it.isNull())
        }.fail {
            fail("fail to call nm_find_next_configuration_height")
        }
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
            assertTrue(!it.isNull())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }

        CliExecution().addBlockchainConfiguration(DEFAULT_APP_CONFIG, newBlockchainRID, 10L,
                Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml",
                getAdminSigner())

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(8L))).success {
            assertTrue(!it.isNull())
        }.fail {
            fail("fail to call nm_find_next_configuration_height")
        }

        client.query("nm_find_next_configuration_height",
                GtvFactory.gtv(
                        "blockchain_rid" to GtvFactory.gtv(newBlockchainRID.hexStringToByteArray()),
                        "height" to GtvFactory.gtv(10L))).success {
            assertTrue(it.isNull())
        }.fail {
            fail("fail to call nm_find_next_configuration_height")
        }
    }
}