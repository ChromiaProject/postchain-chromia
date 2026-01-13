package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
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

class HybridComputeIT : IntegrationTestSetup() {

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
    fun `successful computation of different types`() {
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

        val input2 = CompleteComputation(1, 2L).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success2", "test2", input2)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchComputeResult("success2")?.result).isEqualTo(input2)
        }

        val input3 = CompleteComputation(1, 3L).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success3", "test3", input3)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchComputeResult("success3")?.result).isEqualTo(input3)
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `concurrent computations`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = CompleteComputation(1, 1L).encode()
        val input2 = CompleteComputation(1, 2L).encode()
        val input3 = CompleteComputation(1, 3L).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success", "test", input)
        }
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success2", "test", input2)
        }
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("success3", "test", input3)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsExactlyInAnyOrder(Computation(
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
            ), Computation(
                    id = "success2",
                    state = State.TAKEN,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input2).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = node1Pubkey,
            ), Computation(
                    id = "success3",
                    state = State.NEW,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input3).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = 0,
                    processedBy = ByteArray(0).wrap(),
            ))
            assertThat(query.fetchComputeResult("success")).isNull()
            assertThat(query.fetchComputeResult("success2")).isNull()
            assertThat(query.fetchComputeResult("success3")).isNull()
        }

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            queryAllNodes(chainIid.toLong()) { query ->
                assertThat(query.fetchComputeResult("success")?.result).isEqualTo(input)
                assertThat(query.fetchComputeResult("success2")?.result).isEqualTo(input2)
                assertThat(query.fetchComputeResult("success3")?.result).isEqualTo(input3)
            }
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `failed computation`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = FailComputation(1).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("fail", "test", input)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "fail",
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
            assertThat(query.fetchComputeResult("fail")).isNull()
        }

        Thread.sleep(2 * 1000)
        buildBlock(chainIid.toLong())
        val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
        assertThat(txRid).isNotNull()
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "fail",
                    state = State.FAILED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "Fail",
                    resultTxRid = txRid!!.wrap(),
                    resultOpIndex = 0,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = node1Pubkey,
            ))
            assertThat(query.fetchComputeResult("fail")).isEqualTo(ComputeResult(
                    result = null,
                    error = "Fail",
                    txRid = txRid.wrap(),
                    opIndex = 0,
            ))
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `unexpectedly failed computation`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = ErrorComputation(1).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("error", "test", input)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "error",
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
            assertThat(query.fetchComputeResult("error")).isNull()
        }

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
            assertThat(txRid).isNotNull()
            queryAllNodes(chainIid.toLong()) { query ->
                assertThat(query.fetchRequests()).containsOnly(Computation(
                        id = "error",
                        state = State.FAILED,
                        type = "test",
                        input = GtvEncoder.encodeGtv(input).wrap(),
                        output = ByteArray(0).wrap(),
                        error = "Unknown error",
                        resultTxRid = txRid!!.wrap(),
                        resultOpIndex = 0,
                        takenTimestamp = firstBlockTimestamp,
                        processedBy = node1Pubkey,
                ))
                assertThat(query.fetchComputeResult("error")).isEqualTo(ComputeResult(
                        result = null,
                        error = "Unknown error",
                        txRid = txRid.wrap(),
                        opIndex = 0,
                ))
            }
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `invalid computation`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = InvalidComputation(1).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("invalid", "test", input)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "invalid",
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
            assertThat(query.fetchComputeResult("invalid")).isNull()
        }

        // Wait until computation is finished
        Thread.sleep(2 * 1000)

        // Build four blocks to let the node who took the computation be primary again
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "invalid",
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
            assertThat(query.fetchComputeResult("invalid")).isNull()
        }
    }
}
