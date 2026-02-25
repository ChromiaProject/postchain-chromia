package net.postchain.d1

import assertk.assertThat
import assertk.assertions.hasSize
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.enqueueTx
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class BlockWitnessIT : IntegrationTestSetup() {

    val chainIid = 1
    lateinit var node0Pubkey: WrappedByteArray
    val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        Assertions.assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")

        createNodesFromSystemSetup(sysSetup)
        node0Pubkey = nodes[0].pubKey.hexStringToWrappedByteArray()
        return sysSetup
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun encode() {
        doSystemSetup(nodeCount = 1, "/infra-libs/block_witness_test.xml")

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.addOperation("encode_witness")
        }
        buildBlock(chainIid.toLong())
        assertThat(getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first()))).hasSize(1)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun decode() {
        doSystemSetup(nodeCount = 1, "/infra-libs/block_witness_test.xml")

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.addOperation("decode_witness")
        }
        buildBlock(chainIid.toLong())
        assertThat(getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first()))).hasSize(1)
    }
}
