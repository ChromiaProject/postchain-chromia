package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_container.createContainerWithResourceLimitsOperation
import net.postchain.chain0.model.ContainerResourceLimitType.container_units
import net.postchain.client.core.AsyncQueryResponseStatus
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AsyncQueryIT : ManagedModeBase("async_query") {

    private val dappContainer = "dappContainer"

    init {
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-no-subnodes")

        startNodesAndChain0()
    }

    @AfterAll
    fun tearDown() {
        super.breakdown()
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        getDb(node1).awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider1`() {
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }

        assertAnchoringChainProperties()
    }

    @Test
    @Order(3)
    fun `Add new container`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assertThat(getSummary().containers).isEqualTo(1L)
            }

            transactionBuilder()
                    .createContainerWithResourceLimitsOperation(
                            node1.providerPubkey, dappContainer, systemCluster, 1,
                            listOf(node1.provider.pubKey.data), mapOf(container_units to 1)
                    )
                    .postTransactionUntilConfirmed("$dappContainer container created")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertThat(containers).isEqualTo(setOf(systemContainer, dappContainer))
            }
        }
    }

    @Test
    @Order(4)
    fun `Deploy dapp`() {
        assertThat(node1.c0.getBlockchains(true).size).isEqualTo(3)

        deployDapp("test_async_query", dappContainer, assertSigners = arrayOf(node1))

        // Asserting that blockchain is added
        assertThat(node1.c0.getBlockchains(true).size).isEqualTo(4)
    }

    @Test
    @Order(5)
    fun `Query dapp`() {
        with(node1.client(dapps["test_async_query"]!!)) {
            transactionBuilder().addOperation("add_data", gtv("Heraklion"), gtv(12345L))
                    .postTransactionUntilConfirmed("add_data")

            assertThat(query("quick_query", gtv(mapOf())))
                    .isEqualTo(gtv(listOf(gtv("Heraklion"))))
        }
    }

    @Test
    @Order(6)
    fun `Async query dapp`() {
        with(node1.client(dapps["test_async_query"]!!)) {
            val (endpoint, queryRid) = asyncQuery("quick_query", gtv(mapOf()))
            assertThat(endpoint.url).isEqualTo(config.endpointPool.first().url)
            awaitUntilAsserted {
                val response = fetchAsyncQueryResponse(endpoint, queryRid)
                assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.COMPLETED)
                assertThat(response.queryResponse).isEqualTo(gtv(listOf(gtv("Heraklion"))))
            }
        }
    }

    @Test
    @Order(7)
    fun `Async query timeout`() {
        with(node1.client(dapps["test_async_query"]!!)) {
            transactionBuilder().addOperation("add_bulk_data", gtv(50))
                    .postTransactionUntilConfirmed("add_bulk_data")

            val (endpoint, queryRid) = asyncQuery("slow_query", gtv(mapOf()))
            assertThat(endpoint.url).isEqualTo(config.endpointPool.first().url)
            awaitUntilAsserted(Duration(7, TimeUnit.SECONDS)) {
                val response = fetchAsyncQueryResponse(endpoint, queryRid)
                assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.FAILED)
            }
        }
    }
}
