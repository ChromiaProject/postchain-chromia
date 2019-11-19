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
import kotlin.test.assertEquals

const val adminPrivKey = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
const val adminPubKey = "02487d53cc19d75b12296fd3873ee2f65bdb8e9459cd2ee9bdabd3fc45cb3e87a9"
const val newPeerPubKey = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"

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

    private fun createPostchainTestNodes(nodesCount: Int, configFileName: String) {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        configOverrides.setProperty("api.port", 7740L)
        createNodes(nodesCount, configFileName)
    }

    @Test
    fun testAddPeer() {
        createPostchainTestNodes(1, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())

        val client = getPostchainClient("app.properties")
        client.query("nm_get_peer_infos", GtvFactory.gtv("type" to GtvFactory.gtv("nm_get_peer_infos"))).success {
            val resp = it.asArray()[0].asDict()
            assertEquals("127.0.0.1", resp["host"]?.asString())
            assertEquals(9090L, resp["port"]?.asBigInteger()?.toLong())
        }.fail {
            fail("fail to call nm_get_peer_infos")
        }
    }
}