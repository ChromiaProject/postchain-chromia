package net.postchain.mc.test

import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.CliExecution
import org.junit.Test

val adminPrivKey = "9444bfc21951133b5ae782241dbb6bab8af625c7b2a041f7d0d448de0a697a39"
val adminPubKey = "02487d53cc19d75b12296fd3873ee2f65bdb8e9459cd2ee9bdabd3fc45cb3e87a9"
val newPeerPubKey = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9"

class ManagedNodeTest : IntegrationTest() {

    private fun getAdminSigner(): Pair<ByteArray, ByteArray> {
        return Pair(adminPubKey.hexStringToByteArray(), adminPrivKey.hexStringToByteArray())
    }

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("infrastructure", "base/test")
        configOverrides.setProperty("api.port", -1L)
        val node = createNode(0, "/net/postchain/mc/test/config/blockchain_config.xml")
        CliExecution().addPeer("app.properties", "127.0.0.1", 9090L, newPeerPubKey, getAdminSigner())
        buildBlockAndCommit(node)
    }
}