package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
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
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider4KeyPair
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1DeploymentReplaceGenesisNodeSlowIntegrationTest : ManagedModeBase("replace-genesis") {

    init {
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-no-subnodes")
        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
        node3 = postchainServer("node3",
                provider3KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
        node4 = postchainServer("node4",
                provider4KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
        removeSubnodeContainers()
        startNodesAndChain0()
    }

    @AfterAll
    fun tearDown() {
        super.breakdown()
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

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }
        assertAnchoringChainProperties()

        addSystemNode(node1, node2)
        assertChainSigners(chain0Brid, node1, node2)
        assertChainSigners(clusterAnchoringBrid, node1, node2)
        assertChainSigners(systemAnchoringBrid, node1, node2)

        addSystemNode(node1, node3, listOf(node2.provider))
        assertChainSigners(chain0Brid, node1, node2, node3)
        assertChainSigners(clusterAnchoringBrid, node1, node2, node3)
        assertChainSigners(systemAnchoringBrid, node1, node2, node3)

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

        awaitUntilAsserted(atMost = Duration.ONE_MINUTE) {
            assertThat(node2.c0.getSummary().nodes).isEqualTo(3L)
            assertThat(node3.c0.getSummary().nodes).isEqualTo(3L)
            assertThat(node4.c0.getSummary().nodes).isEqualTo(3L)
        }
    }

    @Test
    @Order(3)
    fun `Add a new system node with non-genesis node as initial peer`() {
        node5 = postchainServer("node5",
                provider1KeyPair,
                "config-no-subnodes"
        )
                .withGenesisNode(node2)

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

        awaitUntilAsserted(atMost = Duration.ONE_MINUTE) {
            assertThat(node2.c0.getSummary().nodes).isEqualTo(4L)
            assertThat(node3.c0.getSummary().nodes).isEqualTo(4L)
            assertThat(node4.c0.getSummary().nodes).isEqualTo(4L)
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