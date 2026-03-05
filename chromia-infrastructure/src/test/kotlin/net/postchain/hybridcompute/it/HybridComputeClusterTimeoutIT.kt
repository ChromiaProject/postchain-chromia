package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
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
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class HybridComputeClusterTimeoutIT : IntegrationTestSetup() {

    val chainIid = 1
    val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        Assertions.assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `compute cluster timeout`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test_cluster_timeout.xml")
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input1 = CompleteComputation(8, 1L).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("timeout", "test", input1)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "timeout",
                    state = State.TAKEN,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input1).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = nodes[1].pubKey.hexStringToWrappedByteArray(),
            ))
            assertThat(query.fetchComputeResult("timeout")).isNull()
        }

        // Wait until cluster compute times out
        Thread.sleep(6 * 1000)
        buildBlock(chainIid.toLong())

        // Creates a failed operation
        buildBlock(chainIid.toLong())

        val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
        assertThat(txRid).isNotNull()
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "timeout",
                    state = State.FAILED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input1).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "Cluster timeout.",
                    resultTxRid = txRid!!.wrap(),
                    resultOpIndex = 0,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = nodes[1].pubKey.hexStringToWrappedByteArray(),
            ))
        }

        val input2 = CompleteComputation(1, 1L).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success", "test", input2)
        }
        val lastBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).contains(Computation(
                    id = "success",
                    state = State.TAKEN,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input2).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = lastBlockTimestamp,
                    processedBy = nodes[0].pubKey.hexStringToWrappedByteArray(),
            ))
            assertThat(query.fetchComputeResult("success")).isNull()
        }

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
            assertThat(txRid).isNotNull()
            queryAllNodes(chainIid.toLong()) { query ->
                assertThat(query.fetchRequests()).contains(Computation(
                        id = "success",
                        state = State.COMPUTED,
                        type = "test",
                        input = GtvEncoder.encodeGtv(input2).wrap(),
                        output = GtvEncoder.encodeGtv(input2).wrap(),
                        error = "",
                        resultTxRid = txRid!!.wrap(),
                        resultOpIndex = 0,
                        takenTimestamp = lastBlockTimestamp,
                        processedBy = nodes[0].pubKey.hexStringToWrappedByteArray(),
                ))
                assertThat(query.fetchComputeResult("success")).isEqualTo(ComputeResult(
                        result = input2,
                        error = null,
                        txRid = txRid.wrap(),
                        opIndex = 0,
                ))
            }
        }
    }
}
