package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.operations.removeNodeOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1ReplaceGenesisNodeSlowIntegrationTest {

    companion object : ManagedModeBase() {
        val node1Logger = KotlinLogging.logger("Deployment_Node1Logger")
        val node2Logger = KotlinLogging.logger("Deployment_Node2Logger")
        val node3Logger = KotlinLogging.logger("Deployment_Node3Logger")
        val node4Logger = KotlinLogging.logger("Deployment_Node4Logger")
        val node5Logger = KotlinLogging.logger("Deployment_Node5Logger")
        override val logsSubdir = "replace-genesis"

        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                    "config-no-subnodes")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            node4 = postchainServer("node4", Slf4jLogConsumer(node4Logger.underlyingLogger, true),
                    KeyPair.of("02B6F2967CF9AFC4D289EF475A2C2DDEC9EAB79AC60C1C99683E3134074619E635", "2C3ED78A578575FD9E67996164A6B281C8AEE29D9AAEE9900088749E33C99150"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            removeSubnodeContainers()
            startNodesAndChain0()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            super.breakdown()
        }
    }

    @Test
    @Order(1)
    fun `Setup the network with 4 nodes`() {
        getDb(node1).awaitBlockHeight(0)
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .updateNodeWithUnitsOperation(
                            node1.providerPubkey, node1.pubkey.data, node1.nodeHost, node1.nodePort.toLong(), node1.nodeApiPath(), 2)
                    .postTransactionUntilConfirmed("init")

            assertThat(getSummary().providers).isEqualTo(1L)
            assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }
        assertAnchoringChainProperties()

        addSystemNode(node1, node2)
        addSystemNode(node1, node3, listOf(node2.provider))
        addSystemNode(node1, node4, listOf(node2.provider, node3.provider))

        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())

        val networkSummary = node1.c0.getSummary()
        assertThat(networkSummary.providers).isEqualTo(4L)
        assertThat(networkSummary.nodes).isEqualTo(4L)
        assertThat(node2.c0.getSummary().nodes).isEqualTo(4L)
        assertThat(node3.c0.getSummary().nodes).isEqualTo(4L)
        assertThat(node4.c0.getSummary().nodes).isEqualTo(4L)
    }

    @Test
    @Order(2)
    fun `Unregister and shut down genesis node`() {
        node1.c0.transactionBuilder()
                .disableNodeOperation(node1.providerPubkey, node1.pubkey.data)
                .removeNodeOperation(node1.providerPubkey, node1.pubkey.data)
                .postTransactionUntilConfirmed("remove genesis node")
        node1.stop()

        assertThat(node2.c0.getSummary().nodes).isEqualTo(3L)
        assertThat(node3.c0.getSummary().nodes).isEqualTo(3L)
        assertThat(node4.c0.getSummary().nodes).isEqualTo(3L)
    }

    @Test
    @Order(3)
    fun `Add a new system node with non-genesis node as initial peer`() {
        node5 = postchainServer("node5", Slf4jLogConsumer(node5Logger.underlyingLogger, true),
                KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                "config-no-subnodes")
                .withEnv("POSTCHAIN_GENESIS_PUBKEY", node2.pubkey.hex())
                .withEnv("POSTCHAIN_GENESIS_HOST", node2.nodeHost)
                .withEnv("POSTCHAIN_GENESIS_PORT", node2.nodePort.toString())

        node5.start()
        val chain0Brid5 = startBlockchain(node5.channel, chain0Config).let { BlockchainRid.buildFromHex(it) }
        assertThat(chain0Brid5).isEqualTo(chain0Brid)

        testLogger.info("Adding new node ${node5.pubkey} to system cluster")
        node2.client(chain0Brid, listOf(node5.provider)).transactionBuilder()
                .registerNodeWithUnitsOperation(
                        node5.providerPubkey,
                        node5.nodeKeyPair.pubKey.data,
                        node5.nodeHost,
                        node5.nodePort.toLong(),
                        node5.nodeApiPath(),
                        listOf(systemCluster),
                        2
                )
                .postTransactionUntilConfirmed("Add new node ${node5.pubkey} to system cluster")

        assertThat(node2.c0.getSummary().nodes).isEqualTo(4L)
        assertThat(node3.c0.getSummary().nodes).isEqualTo(4L)
        assertThat(node4.c0.getSummary().nodes).isEqualTo(4L)
        awaitUntilAsserted(atMost = Duration.ONE_MINUTE) {
            assertThat(node5.c0.getSummary().nodes).isEqualTo(4L)
        }
    }

    private fun addSystemNode(initialNode: PostchainContainer, nodeToAdd: PostchainContainer, voters: List<KeyPair> = listOf()) {
        testLogger.info("Registering ${nodeToAdd.provider.pubKey} as system")
        initialNode.client(chain0Brid, listOf(initialNode.provider, nodeToAdd.provider)).transactionBuilder()
                .registerProviderOperation(initialNode.providerPubkey, nodeToAdd.provider.pubKey, ProviderTier.NODE_PROVIDER)
                .proposeProviderIsSystemOperation(initialNode.providerPubkey, nodeToAdd.providerPubkey, true, "")
                .postTransactionUntilConfirmed("Register ${nodeToAdd.provider.pubKey} as system")

        voteOnAllProposals(voters)

        testLogger.info("Adding node ${nodeToAdd.pubkey} to system cluster")
        initialNode.client(chain0Brid, listOf(nodeToAdd.provider)).transactionBuilder()
                .registerNodeWithUnitsOperation(
                        nodeToAdd.providerPubkey,
                        nodeToAdd.nodeKeyPair.pubKey.data,
                        nodeToAdd.nodeHost,
                        nodeToAdd.nodePort.toLong(),
                        nodeToAdd.nodeApiPath(),
                        listOf(systemCluster),
                        2
                )
                .postTransactionUntilConfirmed("Add node ${nodeToAdd.pubkey} to system cluster")
    }
}