package net.postchain.mc.test

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXDataBuilder
import org.junit.Test

val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()
val newBlockchainRID = "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9".hexStringToByteArray()
val myCS = SECP256K1CryptoSystem()

class ManagedNodeTest : IntegrationTest() {

    private fun makeTx(ownerIdx:Int, host: String, port: Long, brid: ByteArray): ByteArray {
        val owner = KeyPairHelper.pubKey(ownerIdx)
        return GTXDataBuilder(testBlockchainRID, arrayOf(owner), myCS).run {
            addOperation("add_peer", arrayOf(GtvFactory.gtv(host), GtvFactory.gtv(port), GtvFactory.gtv(brid), GtvFactory.gtv(100L)))
            finish()
            sign(myCS.buildSigMaker(owner, KeyPairHelper.privKey(ownerIdx)))
            serialize()
        }
    }

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("infrastructure", "base/test")
        val node = createNode(0, "/net/postchain/mc/test/config/blockchain_config.xml")

        enqueueTx(node, makeTx(0, "127.0.0.1", 9090L, newBlockchainRID), 0)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)
    }
}