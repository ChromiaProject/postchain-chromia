package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import net.postchain.common.createLogCaptor
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
import net.postchain.hybridcompute.HybridComputeSpecialTransactionExtension
import net.postchain.hybridcompute.rell.lib.hybridcompute.Computation
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

// Needs to be in a separate class to get log capturing working
class HybridComputeValidationTimeoutIT : IntegrationTestSetup() {

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
    fun `validation timeout`() {
        val appender = createLogCaptor(HybridComputeSpecialTransactionExtension::class.java, "ValidationTimeout")

        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_test.xml")
        Thread.sleep(3000) // wait for loading to finish
        buildBlock(chainIid.toLong()) // produce positive block timestamp
        val firstBlockTimestamp = getChainNodes(chainIid.toLong()).first().blockQueries(chainIid.toLong()).getLastBlockTimestamp().get()
        assertThat(firstBlockTimestamp).isGreaterThan(0)

        val input = SlowValidationComputation(2).encode()
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitComputeRequestOperation("validation_timeout", "test", input)
        }
        buildBlock(chainIid.toLong())
        queryAllNodes(chainIid.toLong()) { query ->
            assertThat(query.fetchRequests()).containsOnly(Computation(
                    id = "validation_timeout",
                    state = net.postchain.hybridcompute.rell.lib.hybridcompute.State.TAKEN,
                    type = "test",
                    input = GtvEncoder.encodeGtv(input).wrap(),
                    output = ByteArray(0).wrap(),
                    error = "",
                    resultTxRid = ByteArray(0).wrap(),
                    resultOpIndex = -1,
                    takenTimestamp = firstBlockTimestamp,
                    processedBy = node1Pubkey,
            ))
            assertThat(query.fetchComputeResult("validation_timeout")).isNull()
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
                    id = "validation_timeout",
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
            assertThat(query.fetchComputeResult("validation_timeout")).isNull()
        }

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(appender.events.map { it.message.toString() })
                    .contains("Validation of request id [validation_timeout] of type [test] timed out with exception")
        }
    }
}
