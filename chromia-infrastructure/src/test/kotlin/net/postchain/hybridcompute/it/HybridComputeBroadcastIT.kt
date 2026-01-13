package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.common.createLogCaptor
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.ebft.BaseBlockManager
import net.postchain.enqueueTx
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.hybridcompute.rell.lib.hybridcompute.Computation
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeResult
import net.postchain.hybridcompute.rell.lib.hybridcompute.State
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.fetchComputeResult
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.fetchRequests
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.submitComputeRequestOperation
import net.postchain.queryAllNodes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class HybridComputeBroadcastIT : IntegrationTestSetup() {

    val chainIid = 1
    lateinit var node0Pubkey: WrappedByteArray
    lateinit var node1Pubkey: WrappedByteArray
    val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        Assertions.assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")

        createNodesFromSystemSetup(sysSetup)
        node0Pubkey = nodes[0].pubKey.hexStringToWrappedByteArray()
        node1Pubkey = nodes[1].pubKey.hexStringToWrappedByteArray()
        return sysSetup
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `broadcast result`() {
        val appender = createLogCaptor(BaseBlockManager::class.java, "Broadcast")

        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = CompleteComputation(1, 1L).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success", "test", input)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "success",
                    state = State.TAKEN,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = node1Pubkey,
            ))
            assertThat(query.fetchComputeResult("success")).isNull()
        }
        Thread.sleep(2 * 1000)
        buildBlock(chainIid.toLong())
        val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
        assertThat(txRid).isNotNull()
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "success",
                    state = State.COMPUTED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = GtvEncoder.encodeGtv(input).wrap(),
                    error = "",
                    resultTxRid = txRid!!.wrap(),
                    resultOpIndex = 0,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = node1Pubkey,
            ))
            assertThat(query.fetchComputeResult("success")).isEqualTo(ComputeResult(
                    result = input,
                    error = null,
                    txRid = txRid.wrap(),
                    opIndex = 0,
            ))
        }
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())

        assertThat(appender.events.none { it.message.toString().contains("Can't build block") }).isTrue()
    }
}
