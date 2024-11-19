package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import mu.KotlinLogging
import net.postchain.chain0.cm_api.cmGetPeerInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.removeNodeOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_blockchain.proposeForcedConfigurationOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.dapp.stopContainers
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1DeploymentUpdatingSignersOfPausedChainsSlowIntegrationTest {

    companion object : ManagedModeBase() {
        val node1Logger = KotlinLogging.logger("Deployment_Node1Logger")
        val node2Logger = KotlinLogging.logger("Deployment_Node2Logger")
        val node3Logger = KotlinLogging.logger("Deployment_Node3Logger")
        override val logsSubdir = "deployment"

        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                    "config-mix")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-mix")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
                    .withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                    .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                    .withMasterDockerConfig()
                    .withClasspathResourceMapping(
                            "${this::class.java.getResource("config-mix")!!.path.substringAfter("test-classes/")}/node3",
                            PostchainContainer.MOUNT_DIR, BindMode.READ_ONLY
                    )
                    .withEnv("POSTCHAIN_CONFIG", "${PostchainContainer.MOUNT_DIR}/node-config.properties")
                    .withEnv("POSTCHAIN_SUBNODE_LOG4J_CONFIGURATION_FILE", this::class.java.getResource("/log/log4j2.yml")!!.path)

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
    fun `Test propose forced configuration operation`() {

        val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
        val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

        node1.c0.transactionBuilder()
                .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                .postTransactionUntilConfirmed("init")

        assertAnchoringChainProperties()

        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .postTransactionUntilConfirmed("provider2, provider3 registered")

        testLogger.info("Adding cluster/container/node(s)")
        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                .createClusterOperation(node2.providerPubkey, "cluster1", "SYSTEM_P", listOf(node1.providerPubkey, node2.providerPubkey, node3.providerPubkey))
                .createContainerOperation(node2.providerPubkey, "container1", "cluster1", 1, listOf(node1.providerPubkey, node2.providerPubkey, node3.providerPubkey))
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 2)
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf("cluster1"), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf("cluster1"), 2)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, "cluster1")
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, "cluster1")
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, "cluster1")
                .postTransactionUntilConfirmed("cluster1 created")

        awaitUntilAsserted {
            assertThat(node1.c0.getBlockchains(true).size).isEqualTo(4)
        }

        testLogger.info("Deploying dapp to container1")
        deployDapp("test_dapp", "container1")
        val dappBrid = dapps["test_dapp"]!!

        awaitUntilAsserted {
            assertThat(node1.c0.getBlockchains(true).size).isEqualTo(5)
        }

        assertThatDappProcessesTx(dappBrid, "add_city", "Heraklion", "get_cities")

        testLogger.info("Pause chain")
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "(1)Pause chain")
                .postTransactionUntilConfirmed("Change state to ${BlockchainState.PAUSED.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)

        awaitUntilAsserted {
            assertThat(node1.tx(dappBrid, "do_nothing", gtv(2)).second.httpStatusCode!!).isEqualTo(403)
        }
        assertDappQuery(dappBrid, "get_cities", "Heraklion")

        testLogger.info("Remove node2")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .removeNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .postTransactionUntilConfirmed("Remove node2")


        testLogger.info("Remove node3")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .removeNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .postTransactionUntilConfirmed("Remove node3")

        testLogger.info("Stop node2 and node3")
        stopContainers(node2, node3)

        testLogger.info("Resume chain")
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "(1)Change to RUNNING")
                .postTransactionUntilConfirmed("Change state to ${BlockchainState.RUNNING.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        testLogger.info("Verify chain is not working")
        val tx = node1.tx(dappBrid, "do_nothing", gtv(2))
        Awaitility.await().pollDelay(Duration.ONE_MINUTE).pollInterval(Duration.ONE_SECOND).atMost(Duration.FIVE_MINUTES).untilAsserted {
            val checkTxStatus = node1.client(dappBrid).checkTxStatus(tx.second.txRid).status
            assertThat(checkTxStatus).isEqualTo(TransactionStatus.WAITING)
        }
        assertDappQuery(dappBrid, "get_cities", "Heraklion", arrayOf(node1))

        testLogger.info("Pause chain again")
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "(2)Pause chain")
                .postTransactionUntilConfirmed("Change state to ${BlockchainState.PAUSED.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)


        testLogger.info("Propose forced configuration operation")
        val currentHeight = node1.client(dappBrid).currentBlockHeight()
        node1.c0.transactionBuilder().proposeForcedConfigurationOperation(node1.providerPubkey, dappBrid, GtvEncoder.encodeGtv(compileDapp("test_dapp")), currentHeight, "Propose force configuration")
                .postTransactionUntilConfirmed("Force configuration for dapp $dappBrid")

        testLogger.info("Verify one signer")
        awaitUntilAsserted {
            val signers = node1.c0.cmGetPeerInfo(dappBrid.data, currentHeight).map { PubKey(it) }.toSet()
            assertThat(signers.size).isEqualTo(1)
        }

        testLogger.info("Change bc status to RUNNING")
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "(2)Change to RUNNING")
                .postTransactionUntilConfirmed("(3)Change state to ${BlockchainState.RUNNING.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        testLogger.info("Verify bc is running")
        assertThatDappProcessesTx(dappBrid, "add_city", "Heraklion2", "get_cities", node1, arrayOf(node1))
    }


}
