package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_blockchain_move.proposeBlockchainMoveFinishOperation
import net.postchain.chain0.proposal_blockchain_move.proposeBlockchainMoveOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.EndpointPool
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
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
class Directory1MovingMixSlowIntegrationTest {

    companion object : ManagedModeBase() {

        val node1Logger = KotlinLogging.logger("Moving_Node1Logger")
        val node2Logger = KotlinLogging.logger("Moving_Node2Logger")
        val node3Logger = KotlinLogging.logger("Moving_Node3Logger")
        override val logsSubdir = "moving"

        lateinit var dappBrid: BlockchainRid
        lateinit var s1CAC: BlockchainRid
        lateinit var s2CAC: BlockchainRid
        lateinit var s3CAC: BlockchainRid

        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    provider1KeyPair,
                    "config-mix")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    provider2KeyPair,
                    "config-mix")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    provider3KeyPair,
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
    fun `Setup the network`() {
        getDb(node1).awaitBlockHeight(0)
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 2)
                    .postTransactionUntilConfirmed("init")

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }
        assertAnchoringChainProperties()

        testLogger.info("Adding system providers provider2 and provider3 and their nodes")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System provider2, provider3 registered, node2, node3 added to the system cluster")

        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(2)
    fun `Add clusters, containers and blockchain`() {
        testLogger.info("Adding cluster/container/node(s): s1/c1/node1, s2/c2/node2, s3/c3a,c3b/node3")
        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                // s1/c1/node1
                .createClusterOperation(node1.providerPubkey, "s1", "SYSTEM_P", listOf(node1.providerPubkey))
                .createContainerOperation(node1.providerPubkey, "c1", "s1", 1, listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, "s1")
                // s1/c2/node2
                .createClusterOperation(node2.providerPubkey, "s2", "SYSTEM_P", listOf(node2.providerPubkey))
                .createContainerOperation(node2.providerPubkey, "c2", "s2", 1, listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, "s2")
                // s3/c3a,c3b/node3
                .createClusterOperation(node3.providerPubkey, "s3", "SYSTEM_P", listOf(node3.providerPubkey))
                .createContainerOperation(node3.providerPubkey, "c3a", "s3", 1, listOf(node1.providerPubkey))
                .createContainerOperation(node3.providerPubkey, "c3b", "s3", 1, listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, "s3")
                .postTransactionUntilConfirmed("s1/c1/node1, s2/c2/node2, s3/c3a,c3b/node3 created")

        testLogger.info("Deploying dapp to c1")
        awaitUntilAsserted {
            assertThat(node1.c0.getBlockchains(true).size).isEqualTo(6)
        }
        deployDapp("test_dapp", "c1", assertSigners = arrayOf(node1))
        awaitUntilAsserted {
            assertThat(node1.c0.getBlockchains(true).size).isEqualTo(7)
        }

        dappBrid = dapps["test_dapp"]!!
        s1CAC = BlockchainRid(node1.c0.cmGetClusterInfo("s1").anchoringChain)
        s2CAC = BlockchainRid(node1.c0.cmGetClusterInfo("s2").anchoringChain)
        s3CAC = BlockchainRid(node1.c0.cmGetClusterInfo("s3").anchoringChain)

        testLogger.info("Post a transaction to the dapp that we can prove after moving")
        assertThatDappProcessesTx(dapps["test_dapp"]!!, "add_city", "Heraklion", "get_cities", node1, arrayOf(node1))

        testLogger.info("Making sure 3 blocks of dapp are anchored")
        awaitUntilAsserted {
            val lastAnchoredBlock = awaitQueryResult {
                node1.client(s1CAC).getLastAnchoredBlock(dappBrid)
            }
            assertThat(lastAnchoredBlock!!.blockHeight).isGreaterThan(3)
        }

        updateDapp("test_dapp", maxBlockTransactions = 1000)

        testLogger.info("Making sure 1 more block of dapp is anchored")
        awaitUntilAsserted {
            val lastAnchoredBlock = awaitQueryResult {
                node1.client(s1CAC).getLastAnchoredBlock(dappBrid)
            }
            assertThat(lastAnchoredBlock!!.blockHeight).isGreaterThan(6)
        }
    }

    @Test
    @Order(3)
    fun `Move blockchain from container c1 on node1-master to container c3a on node3-subnode`() {
        testLogger.info("Move blockchain from container c1 on node1-master to container c3a on node3-subnode")

        // build 3 more blocks
        testLogger.info("Making sure next 3 blocks of dapp are built and anchored")
        val dappClient = node1.client(dappBrid)
        val height = dappClient.currentBlockHeight()
        awaitUntilAsserted {
            assertThat(dappClient.currentBlockHeight()).isGreaterThan(height + 2)
        }

        // pausing blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("test_dapp paused")
        // verify blockchain is PAUSED and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)

        // initiating moving
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveOperation(node1.providerPubkey, dappBrid, "c3a", "")
                .postTransactionUntilConfirmed("test_dapp moving to c3a/subnode started")

        // finalizing moving
        val lastHeight = dappClient.currentBlockHeight() - 1
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveFinishOperation(node1.providerPubkey, dappBrid, lastHeight, "")
                .postTransactionUntilConfirmed("test_dapp moving to c3a/subnode finalized")

        // resuming blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "")
                .postTransactionUntilConfirmed("test_dapp resumed")
        // verify blockchain is RUNNING and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        // Asserting that all blocks (some of them) are anchored on s3CAC chain
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC, 0)
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC)

        // Asserting that new blocks are built and anchored on s3CAC chain
        awaitUntilAsserted {
            val s3LastAnchoredHeight = node3.client(s3CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(s3LastAnchoredHeight).isGreaterThan(lastHeight)
        }
    }

    @Test
    @Order(4)
    fun `Move blockchain from container c3a on node3-subnode-c3a to container c3b on node3-subnode-c3a`() {
        testLogger.info("Move blockchain from container c3a on node3-subnode-c3a to container c3b on node3-subnode-c3a")

        // build 3 more blocks
        testLogger.info("Making sure next 3 blocks of dapp are built and anchored")
        val dappClient = node3.client(dappBrid)
        val height = dappClient.currentBlockHeight()
        awaitUntilAsserted {
            assertThat(dappClient.currentBlockHeight()).isGreaterThan(height + 2)
        }

        // pausing blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("test_dapp paused")
        // verify blockchain is PAUSED and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)

        // initiating moving
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveOperation(node1.providerPubkey, dappBrid, "c3b", "")
                .postTransactionUntilConfirmed("test_dapp moving to c3b/subnode started")

        // finalizing moving
        val lastHeight = dappClient.currentBlockHeight() - 1
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveFinishOperation(node1.providerPubkey, dappBrid, lastHeight, "")
                .postTransactionUntilConfirmed("test_dapp moving to c3b/subnode finalized")

        // resuming blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "")
                .postTransactionUntilConfirmed("test_dapp resumed")
        // verify blockchain is RUNNING and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        // Asserting that all blocks (some of them) are anchored on s3CAC chain
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC, 0)
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC)

        // Asserting that new blocks are built and anchored on s3CAC chain
        awaitUntilAsserted {
            val s3LastAnchoredHeight = node3.client(s3CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(s3LastAnchoredHeight).isGreaterThan(lastHeight)
        }
    }

    @Test
    @Order(5)
    fun `Move blockchain from container c3b on node3-subnode-c3b to container c2 on node2-master`() {
        testLogger.info("Move blockchain from container c3b on node3-subnode-c3b to container c2 on node2-master")

        // build 3 more blocks
        testLogger.info("Making sure next 3 blocks of dapp are built and anchored")
        val dappClient = node3.client(dappBrid)
        val height = dappClient.currentBlockHeight()
        awaitUntilAsserted {
            assertThat(dappClient.currentBlockHeight()).isGreaterThan(height + 2)
        }

        // pausing blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("test_dapp paused")
        // verify blockchain is PAUSED and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)

        // initiating moving
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveOperation(node1.providerPubkey, dappBrid, "c2", "")
                .postTransactionUntilConfirmed("test_dapp moving to c2/master started")

        // finalizing moving
        val lastHeight = dappClient.currentBlockHeight() - 1
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainMoveFinishOperation(node1.providerPubkey, dappBrid, lastHeight, "")
                .postTransactionUntilConfirmed("test_dapp moving to c2/master finalized")

        // resuming blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "")
                .postTransactionUntilConfirmed("test_dapp resumed")
        // verify blockchain is RUNNING and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        // Asserting that all blocks (some of them) are anchored on s2CAC chain
        assertBlockReanchored(dappBrid, node3, s3CAC, node2, s2CAC, 0)
        assertBlockReanchored(dappBrid, node3, s3CAC, node2, s2CAC)

        // Asserting that new blocks are anchored on s2CAC chain
        awaitUntilAsserted {
            val s2LastAnchoredHeight = node2.client(s2CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(s2LastAnchoredHeight).isGreaterThan(lastHeight)
        }
    }

    @Test
    @Order(6)
    fun `Transactions can be proven with ICCF after moving`() {
        // Deploy a target chain (important thing is that it is in another cluster in order to avoid intra-cluster ICCF)
        testLogger.info("Deploying ICCF target chain")
        deployDapp("test_dapp2", "c1", icmfReceiver = dapps["test_dapp"]!!.data, assertSigners = arrayOf(node1))
        awaitUntilAsserted {
            assertThat(node1.c0.getBlockchains(true).size).isEqualTo(8)
        }

        val chromiaClientProvider = ChromiaClientProvider(
                ContainerClusterManagement(
                        ClusterManagementImpl(node1.c0), mapOf("s1" to listOf(node1.peerInfo()), "s2" to listOf(node2.peerInfo()), "s3" to listOf(node3.peerInfo()))
                ),
                PostchainClientConfig(BlockchainRid.ZERO_RID, EndpointPool.singleUrl(""), merkleHashVersion = 2)
        )
        verifyICCF(chromiaClientProvider, arrayOf(node1))
    }
}