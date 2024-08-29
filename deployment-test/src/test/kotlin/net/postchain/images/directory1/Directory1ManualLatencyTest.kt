package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import eu.rekawek.toxiproxy.model.ToxicDirection
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerWithUnitsOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.crypto.KeyPair
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * This test case is intended to be used as a stub for creating tests with different network conditions such as
 * latencies, broken connections etc.
 *
 * The default network setup is this:
 *
 * System cluster: node1
 *
 * Test cluster: node1, node2, node3, node4
 *
 * Node4 has 50ms latency up and downstream to all other nodes. Toxiproxy offers other types of network simulation.
 * Please see testcontainers toxiproxy documentation.
 *
 * How it works:
 * Since we can't really know which port a node will use when connecting as a client we need to do proxy on server port.
 * The trick is then to update peer information to point to proxy instead of directly at the node. Depending on scenario
 * this may require some extra hoops.
 *
 * E.g. in the default scenario all nodes should talk to node4 via proxy b/c we want to add latency to all communication
 * to node4. Therefore, we can simply register the proxy address and port as node4's peer info in directory chain.
 * However, we only want node4 to have extra latency on the other nodes server ports so for that case we have to add
 * local peer info overrides to node4's database to point to their proxies.
 *
 * It should be possible to have multiple proxies towards the same node for more complex scenarios.
 */
@Testcontainers
@Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1ManualLatencyTest {

    companion object : ManagedModeBase() {
        const val TEST_CLUSTER = "test_cluster"
        const val TEST_CONTAINER = "test_container"

        const val TOXI_PROXY_HOST = "toxi"

        val node1Logger = KotlinLogging.logger("Latency_Node1Logger")
        val node2Logger = KotlinLogging.logger("Latency_Node2Logger")
        val node3Logger = KotlinLogging.logger("Latency_Node3Logger")
        val node4Logger = KotlinLogging.logger("Latency_Node4Logger")

        override val logsSubdir = "latency"

        private val toxiProxyContainer = ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
                .withNetworkAliases(TOXI_PROXY_HOST)
                .withNetwork(network)

        private val node1Proxy: ToxiproxyContainer.ContainerProxy
        private val node2Proxy: ToxiproxyContainer.ContainerProxy
        private val node3Proxy: ToxiproxyContainer.ContainerProxy
        private val node4Proxy: ToxiproxyContainer.ContainerProxy
        private val allProxies: List<ToxiproxyContainer.ContainerProxy>

        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                    "config-no-subnodes")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-no-subnodes")
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-no-subnodes")
            node4 = postchainServer("node4", Slf4jLogConsumer(node4Logger.underlyingLogger, true),
                    KeyPair.of("02B6F2967CF9AFC4D289EF475A2C2DDEC9EAB79AC60C1C99683E3134074619E635", "2C3ED78A578575FD9E67996164A6B281C8AEE29D9AAEE9900088749E33C99150"),
                    "config-no-subnodes")

            removeSubnodeContainers()
            startNodesAndChain0()
            toxiProxyContainer.start()
            node1Proxy = toxiProxyContainer.getProxy(node1, node1.nodePort)
            node2Proxy = toxiProxyContainer.getProxy(node2, node2.nodePort)
            node3Proxy = toxiProxyContainer.getProxy(node3, node3.nodePort)
            node4Proxy = toxiProxyContainer.getProxy(node4, node4.nodePort)
            allProxies = listOf(node1Proxy, node2Proxy, node3Proxy, node4Proxy)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            toxiProxyContainer.stop()
            super.breakdown()
        }
    }

    @Test
    @Order(1)
    fun `Setup the network`() {
        getDb(node1).awaitBlockHeight(0)
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 2)
                    .postTransactionUntilConfirmed("init")

            assertThat(getSummary().providers).isEqualTo(1L)
            assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }
        assertAnchoringChainProperties()

        testLogger.info("Adding providers provider2, provider3 and provider4")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com"),
                ProviderInfo(node4.provider.pubKey.wData, "provider4", "http://provider4.com")
        )

        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = false, active = true, description = "")
                .postTransactionUntilConfirmed("Providers provider2, provider3, provider4 registered")

        testLogger.info("Create test cluster and add node1 to it")
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .createClusterOperation(node1.providerPubkey, TEST_CLUSTER, "SYSTEM_P", listOf(node1.providerPubkey, node2.providerPubkey, node3.providerPubkey, node4.providerPubkey))
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, TEST_CLUSTER)
                .postTransactionUntilConfirmed("Created test cluster")

        testLogger.info("Register node2, node3, node4 and add to test cluster")
        node1.client(chain0Brid, listOf(node2.provider, node3.provider, node4.provider)).transactionBuilder().addNop()
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(TEST_CLUSTER), 1)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(TEST_CLUSTER), 1)
                .registerNodeWithUnitsOperation(node4.providerPubkey, node4.pubkey.data, TOXI_PROXY_HOST, node4Proxy.originalProxyPort.toLong(), node4.nodeApiPath(), listOf(TEST_CLUSTER), 1)
                .postTransactionUntilConfirmed("Registered nodes to test cluster")
    }

    @Test
    @Order(2)
    fun `Add new container`() {
        testLogger.info("Adding container")
        with(node1.c0) {
            transactionBuilder()
                    .createContainerWithUnitsOperation(
                            node1.providerPubkey, TEST_CONTAINER, TEST_CLUSTER, 1,
                            listOf(node1.provider.pubKey.data), 4
                    )
                    .postTransactionUntilConfirmed("$TEST_CONTAINER container created")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertThat(containers).contains(TEST_CONTAINER)
            }
        }
    }

    /* INSERT ADDITIONAL NETWORK SETUP HERE */

    @Test
    @Order(100)
    fun `Set up proxy delay`() {
        testLogger.info("Overriding peer infos for node4 to point to proxies")
        overrideNode4PeerInfo(node1.pubkey, TOXI_PROXY_HOST, node1Proxy.originalProxyPort)
        overrideNode4PeerInfo(node2.pubkey, TOXI_PROXY_HOST, node2Proxy.originalProxyPort)
        overrideNode4PeerInfo(node3.pubkey, TOXI_PROXY_HOST, node3Proxy.originalProxyPort)
        restartNode(node4) // Restart node to apply new peer information

        /* OVERRIDE WITH PREFERRED PROXY CONDITIONS */
        testLogger.info("Add latencies")
        allProxies.forEachIndexed { index, proxy ->
            proxy.toxics().latency("latency_down_node$index", ToxicDirection.DOWNSTREAM, 50)
            proxy.toxics().latency("latency_up_node$index", ToxicDirection.UPSTREAM, 50)
        }
    }

    /* INSERT TESTS HERE */
}
