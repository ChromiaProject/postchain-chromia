package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import net.postchain.common.wrap
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.enqueueTx
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.hybridcompute.rell.lib.hybridcompute.Computation
import net.postchain.hybridcompute.rell.lib.hybridcompute.State
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.HcFetchComputeResultResult
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.fetchComputeResult
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.fetchRequests
import net.postchain.hybridcompute.rell.lib.hybridcompute.test.submitComputeRequestOperation
import net.postchain.query
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class HybridComputeIT : IntegrationTestSetup() {

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
    fun `successful computation`() {
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test.xml")

        val input = CompleteComputation(1).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
             it.submitComputeRequestOperation("success", "test", input)
        }
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "success",
                state = State.NEW,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))

        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "success",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("success")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                    id = "success",
                    state = State.COMPUTED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = GtvEncoder.encodeGtv(input).wrap(),
                    error = ""
            ))
            assertThat(query(chainIid.toLong()).fetchComputeResult("success")).isEqualTo(HcFetchComputeResultResult(
                    result = input,
                    error = null
            ))
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `failed computation`() {
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test.xml")

        val input = FailComputation(1).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
             it.submitComputeRequestOperation("fail", "test", input)
        }
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "fail",
                state = State.NEW,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))

        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "fail",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("fail")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                    id = "fail",
                    state = State.FAILED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "Fail"
            ))
            assertThat(query(chainIid.toLong()).fetchComputeResult("fail")).isEqualTo(HcFetchComputeResultResult(
                    result = null,
                    error = "Fail"
            ))
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `unexpectedly failed computation`() {
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test.xml")

        val input = ErrorComputation(1).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
             it.submitComputeRequestOperation("error", "test", input)
        }
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "error",
                state = State.NEW,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))

        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "error",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("error")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                    id = "error",
                    state = State.FAILED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "Unknown error"
            ))
            assertThat(query(chainIid.toLong()).fetchComputeResult("error")).isEqualTo(HcFetchComputeResultResult(
                    result = null,
                    error = "Unknown error"
            ))
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `timed out computation`() {
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test.xml")

        val input = CompleteComputation(8).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
             it.submitComputeRequestOperation("timeout", "test", input)
        }
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "timeout",
                state = State.NEW,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))

        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "timeout",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("timeout")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))

        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            buildBlock(chainIid.toLong())
            assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                    id = "timeout",
                    state = State.FAILED,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "Computation timed out after 5 seconds"
            ))
            assertThat(query(chainIid.toLong()).fetchComputeResult("timeout")).isEqualTo(HcFetchComputeResultResult(
                    result = null,
                    error = "Computation timed out after 5 seconds"
            ))
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `invalid computation`() {
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test.xml")

        val input = InvalidComputation(1).encode()

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
             it.submitComputeRequestOperation("invalid", "test", input)
        }
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "invalid",
                state = State.NEW,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))

        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "invalid",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("invalid")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))

        // Wait until computation is finished
        Thread.sleep(2*1000)

        // Build four blocks to let the node who took the computation be primary again
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        buildBlock(chainIid.toLong())
        assertThat(query(chainIid.toLong()).fetchRequests()).containsOnly(Computation(
                id = "invalid",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = ""
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("invalid")).isEqualTo(HcFetchComputeResultResult(
                result = null,
                error = null
        ))
    }
}
