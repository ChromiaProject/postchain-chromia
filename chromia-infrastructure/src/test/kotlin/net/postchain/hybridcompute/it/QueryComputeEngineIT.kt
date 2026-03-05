package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.enqueueTx
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.hybridcompute.rell.lib.hybridcompute_query.test.fetchQueryComputeResult
import net.postchain.hybridcompute.rell.lib.hybridcompute_query.test.submitQueryComputeRequestOperation
import net.postchain.queryAllNodes
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class QueryComputeEngineIT : IntegrationTestSetup() {

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
    fun `fail query computation due to no such query`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_query_test.xml")
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitQueryComputeRequestOperation("id-0", "no-such-query", mapOf("dummy" to gtv(1)))
        }
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(chainIid.toLong())
            queryAllNodes(chainIid.toLong()) { query ->
                val result = query.fetchQueryComputeResult("id-0")
                assertThat(result).isNotNull()
                assertThat(result?.result).isNull()
                assertThat(result?.error).isEqualTo("Query no-such-query not found")
            }
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `fail query computation due to incorrect args`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_query_test.xml")
        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitQueryComputeRequestOperation("id-0", "test_query", mapOf("dummy" to gtv(1)))
        }
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(chainIid.toLong())
            queryAllNodes(chainIid.toLong()) { query ->
                val result = query.fetchQueryComputeResult("id-0")
                assertThat(result).isNotNull()
                assertThat(result?.result).isNull()
                assertThat(result?.error).isEqualTo("Query 'test_query' failed: Invalid argument(s): dummy")
            }
        }
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `successful query computation`() {
        doSystemSetup(nodeCount = 4, "/infra-libs/hybridcompute_query_test.xml")

        enqueueTx(chainIid.toLong(), merkleHashCalculator) {
            it.submitQueryComputeRequestOperation("id-0", "test_query", mapOf("input" to gtv("in")))
        }
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(chainIid.toLong())
            queryAllNodes(chainIid.toLong()) { query ->
                val result = query.fetchQueryComputeResult("id-0")
                assertThat(result).isNotNull()
                assertThat(result?.error).isNull()
                assertThat(result?.result)
                        .isNotNull()
                        .equals(gtv("in-out"))
            }
        }
    }
}
