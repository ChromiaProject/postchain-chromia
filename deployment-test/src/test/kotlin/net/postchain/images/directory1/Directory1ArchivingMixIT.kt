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
import net.postchain.chain0.proposal_blockchain.proposeBlockchainUnarchiveActionOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.mandas.docker.client.DockerClient
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1ArchivingMixIT {

    companion object : ManagedModeBase() {

        val node1Logger = KotlinLogging.logger("Archiving_Node1Logger")
        val node2Logger = KotlinLogging.logger("Archiving_Node2Logger")
        val node3Logger = KotlinLogging.logger("Archiving_Node3Logger")

        lateinit var dappBrid: BlockchainRid
        lateinit var s1CAC: BlockchainRid
        lateinit var s2CAC: BlockchainRid
        lateinit var s3CAC: BlockchainRid

        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                    "config-mix")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix")
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-mix")
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
        node1Db.awaitBlockHeight(0)
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
        testLogger.info("Adding cluster/container/node(s): s1/c1/node1, s2/c2/node2, s3/c3/node3")
        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                // s1/c1/node1
                .createClusterOperation(node1.providerPubkey, "s1", "SYSTEM_P", listOf(node1.providerPubkey))
                .createContainerOperation(node1.providerPubkey, "c1", "s1", 1, listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, "s1")
                // s1/c2/node2
                .createClusterOperation(node2.providerPubkey, "s2", "SYSTEM_P", listOf(node2.providerPubkey))
                .createContainerOperation(node2.providerPubkey, "c2", "s2", 1, listOf(node1.providerPubkey, node2.providerPubkey))
                .updateNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, "s2")
                // s3/c3/node3
                .createClusterOperation(node3.providerPubkey, "s3", "SYSTEM_P", listOf(node3.providerPubkey))
                .createContainerOperation(node3.providerPubkey, "c3", "s3", 1, listOf(node1.providerPubkey, node3.providerPubkey))
                .updateNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, "s3")
                .postTransactionUntilConfirmed("s1/c1/node1, s2/c2/node2, s3/c3/node3 created")

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

        testLogger.info("Making sure 10 blocks of dapp are anchored")
        awaitUntilAsserted {
            val lastAnchoredBlock = awaitQueryResult {
                node1.client(s1CAC).getLastAnchoredBlock(dappBrid)
            }
            assertThat(lastAnchoredBlock!!.blockHeight).isGreaterThan(10)
        }
    }

    @Test
    @Order(3)
    fun `Archiving blockchain in container c1 running on master`() {
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.archive, "")
                .postTransactionUntilConfirmed("test_dapp archived in c1 running on master")
        verifyBlockchainState(node1, dappBrid, BlockchainState.ARCHIVED)
        awaitUntilAsserted {
            assertThat(node1Db.isChainArchivedOnNode(dappBrid)).isTrue()
        }
    }

    @Test
    @Order(4)
    fun `Unarchiving blockchain to container c3 running on subnode`() {
        val s1LastAnchoredHeight = node1.client(s1CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainUnarchiveActionOperation(node1.providerPubkey, dappBrid, "c3", s1LastAnchoredHeight, "")
                .postTransactionUntilConfirmed("test_dapp unarchiving to c3/subnode started")

        // Asserting that state changed: ARCHIVED -> UNARCHIVING
        verifyBlockchainState(node1, dappBrid, BlockchainState.UNARCHIVING)

        // Asserting that blockchain is UNARCHIVED, i.e. state changed: UNARCHIVING -> RUNNING
        verifyBlockchainState(node3, dappBrid, BlockchainState.RUNNING)

        // Asserting that all blocks (some of them) are anchored on s3CAC chain
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC, 0)
        assertBlockReanchored(dappBrid, node1, s1CAC, node3, s3CAC, s1LastAnchoredHeight)

        // Asserting that new blocks are anchored on s3CAC chain
        awaitUntilAsserted {
            val s3LastAnchoredHeight = node3.client(s3CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(s3LastAnchoredHeight).isGreaterThan(s1LastAnchoredHeight)
        }
    }

    @Test
    @Order(5)
    fun `Archiving blockchain in container c3 running on subnode`() {
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.archive, "")
                .postTransactionUntilConfirmed("test_dapp archived in c3 running on subnode")
        verifyBlockchainState(node1, dappBrid, BlockchainState.ARCHIVED)

        awaitUntilAsserted {
            val fooDockerContainer = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).firstOrNull {
                it.names().any { name -> name.contains("-c3-") }
            }
            assertThat(fooDockerContainer?.state()).isEqualTo("exited")
        }
    }

    @Test
    @Order(6)
    fun `Unarchiving blockchain to container c2 running on master`() {
        val s3LastAnchoredHeight = node3.client(s3CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainUnarchiveActionOperation(node1.providerPubkey, dappBrid, "c2", s3LastAnchoredHeight, "")
                .postTransactionUntilConfirmed("test_dapp unarchiving to c2/master started")

        // Asserting that state changed: ARCHIVED -> UNARCHIVING
        verifyBlockchainState(node3, dappBrid, BlockchainState.UNARCHIVING)

        // Asserting that blockchain is UNARCHIVED, i.e. state changed: UNARCHIVING -> RUNNING
        verifyBlockchainState(node2, dappBrid, BlockchainState.RUNNING)

        // Asserting that old blocks (some of them) are anchored on s2CAC chain
        assertBlockReanchored(dappBrid, node3, s3CAC, node2, s2CAC, 0)
        assertBlockReanchored(dappBrid, node3, s3CAC, node2, s2CAC, s3LastAnchoredHeight)

        // Asserting that new blocks are anchored on s2CAC chain
        awaitUntilAsserted {
            val s2LastAnchoredHeight = node2.client(s2CAC).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(s2LastAnchoredHeight).isGreaterThan(s3LastAnchoredHeight)
        }
    }
}