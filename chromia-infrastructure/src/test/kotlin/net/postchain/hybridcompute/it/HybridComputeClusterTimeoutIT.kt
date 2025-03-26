package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.wrap
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.enqueueTx
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.hybridcompute.rell.lib.hybridcompute.Computation
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeResult
import net.postchain.hybridcompute.rell.lib.hybridcompute.State
import net.postchain.hybridcompute.rell.lib.hybridcompute.clusterTimeoutOperation
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

class HybridComputeClusterTimeoutIT : IntegrationTestSetup(){

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
        doSystemSetup(nodeCount = 4, "/hybridcompute/hybridcompute_test_cluster_timeout.xml")

        val input = CompleteComputation(6).encode()

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
                error = "",
                resultTxRid = ByteArray(0).wrap(),
                resultOpIndex = -1,
                takenTimestamp = 0
        ))

        buildBlock(chainIid.toLong())
        val takenRequests = query(chainIid.toLong()).fetchRequests()
        assertThat(takenRequests).containsOnly(Computation(
                id = "timeout",
                state = State.TAKEN,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = "",
                resultTxRid = ByteArray(0).wrap(),
                resultOpIndex = -1,
                takenTimestamp = takenRequests[0].takenTimestamp
        ))
        assertThat(query(chainIid.toLong()).fetchComputeResult("timeout")).isNull()

        // Make next node primary
        buildBlock(chainIid.toLong())

        // Wait until cluster compute times out
        Thread.sleep(6 * 1000)

        // Creates failed operation
        buildBlock(chainIid.toLong())

        val txRid = getTxRidsAtHeight(nodes.first(), getLastHeight(nodes.first())).firstOrNull()
        assertThat(txRid).isNotNull()
        val failedRequest = query(chainIid.toLong()).fetchRequests()
        assertThat(failedRequest).containsOnly(Computation(
                id = "timeout",
                state = State.FAILED,
                type = "test",
                input = GtvEncoder.encodeGtv(input).wrap(),
                output = ByteArray(0).wrap(),
                error = "Cluster timeout.",
                resultTxRid = txRid!!.wrap(),
                resultOpIndex = 0,
                takenTimestamp = failedRequest[0].takenTimestamp
        ))
    }
}