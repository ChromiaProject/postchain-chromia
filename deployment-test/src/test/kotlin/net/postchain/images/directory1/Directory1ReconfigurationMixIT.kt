package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.chain0.common.operations.enableNodeOperation
import net.postchain.chain0.common.operations.registerNodeOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_cluster.proposeClusterProviderOperation
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.resources.*
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
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
class Directory1ReconfigurationMixIT {

    companion object : ManagedModeBase() {

        val node1Logger = KotlinLogging.logger("Reconfig_Node1Logger")
        val node2Logger = KotlinLogging.logger("Reconfig_Node2Logger")
        val node3Logger = KotlinLogging.logger("Reconfig_Node3Logger")

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
    fun `Initialize network with provider1`() {
        node1Db.awaitBlockHeight(0)
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")

            assertThat(getSummary().providers).isEqualTo(1L)
            assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }
        assertAnchoringChainProperties()
    }

    @Test
    @Order(2)
    fun `Add node2 as signer to c0`() {
        testLogger.info("Adding node2 to the cluster")
        testLogger.info("Registering provider2")

        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder()
                .registerProviderOperation(node1.providerPubkey, node2.provider.pubKey, ProviderTier.NODE_PROVIDER)
                .proposeProviderIsSystemOperation(node1.providerPubkey, node2.providerPubkey, true, "")
                .postTransactionUntilConfirmed("Register p2 as system")

        node1.client(chain0Brid, listOf(node2.provider)).transactionBuilder()
                .registerNodeOperation(
                        node2.providerPubkey,
                        node2.nodeKeyPair.pubKey.data,
                        node2.nodeHost,
                        node2.nodePort.toLong(),
                        node2.nodeApiPath(),
                        listOf(systemCluster)
                )
                .postTransactionUntilConfirmed("add node 2 to system cluster")

        // Asserting that node1, node2 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, node1, node2)
        assertChainSigners(clusterAnchoringBrid, node1, node2)
        assertChainSigners(systemAnchoringBrid, node1, node2)
    }

    @Test
    @Order(3)
    fun `Add node3 as signer to c0`() {
        testLogger.info("Adding node3 to the cluster")
        testLogger.info("Registering provider3")

        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder()
                .registerProviderOperation(node1.providerPubkey, node3.provider.pubKey, ProviderTier.NODE_PROVIDER)
                .proposeProviderIsSystemOperation(node1.providerPubkey, node3.providerPubkey, true, "")
                .postTransactionUntilConfirmed("Register p3 as system")

        voteOnAllProposals(node2.provider)

        testLogger.info("Adding node3 to [node1, node2] network")
        node1.client(chain0Brid, listOf(node3.provider)).transactionBuilder()
                .registerNodeOperation(
                        node3.providerPubkey,
                        node3.pubkey.data,
                        node3.nodeHost,
                        node3.nodePort.toLong(),
                        node3.nodeApiPath(),
                        listOf(systemCluster)
                )
                .postTransactionUntilConfirmed("add node 3 to system cluster")

        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(4)
    fun `Disable and re-enable node2 and node3`() {
        testLogger.info("Disable and re-enable node2 and node3")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .postTransactionUntilConfirmed("Propose disabling node2 and node3")

        assertChainSigners(chain0Brid, node1)
        assertChainSigners(clusterAnchoringBrid, node1)
        assertChainSigners(systemAnchoringBrid, node1)

        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider))
                .enableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, systemCluster)
                .postTransactionUntilConfirmed("Enable and add node2 to the $systemCluster cluster")

        node1.c0.transactionBuilder(listOf(node1.provider, node3.provider))
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                .postTransactionUntilConfirmed("Enable and add node3 to the $systemCluster cluster")

        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(5)
    fun `Reconfigure CAC by various config types`() {
        testLogger.info("Update cluster anchoring chain")

        // 1. Mixed tx: proposing config, duplicated config, removing signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for CAC: config, duplicated config, removing signer, faulty config, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18200), "")
                // propose the same config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18200), "")
                // disable node3
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18300, true), "")
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18400), "")
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs")

        voteOnAllProposals(node2.provider)
        voteOnAllProposals(node3.provider)

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, clusterAnchoringBrid).toSet())
            assertEquals(
                    setOf(500, 18200, 18400),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, clusterAnchoringBrid))
        }

        // 2. Mixed tx: proposing config, faulty config, adding signer, config again
        testLogger.info("Proposing different kinds of configs for CAC: proposing config, faulty config, adding signer, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose already applied config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18400), "")
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18500, true), "")
                // re-enable node3
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18600), "")
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs #2")

        voteOnAllProposals(node2.provider)
        voteOnAllProposals(node3.provider)

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData, node3.pubkey.wData),
                    getLastBlockConfigSigners(node1, clusterAnchoringBrid).toSet())
            assertEquals(
                    setOf(500, 18200, 18400, 18600),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, clusterAnchoringBrid))
        }
    }

    @Test
    @Order(6)
    fun `Reconfigure SAC by various config types`() {
        testLogger.info("Update system anchoring chain")

        // 1. Mixed tx: proposing config, duplicated config, removing signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for SAC: config, duplicated config, removing signer, faulty config, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19200), "")
                // propose the same config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19200), "")
                // disable node3
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19300, true), "")
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19400), "")
                .postTransactionUntilConfirmed("Propose different system anchoring chain configs")

        voteOnAllProposals(node2.provider)
        voteOnAllProposals(node3.provider)

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, systemAnchoringBrid).toSet())
            assertEquals(
                    setOf(500, 19200, 19400),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, systemAnchoringBrid))
        }

        // 2. Mixed tx: proposing config, faulty config, adding signer, config again
        testLogger.info("Proposing different kinds of configs for SAC: proposing config, faulty config, adding signer, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose already applied config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19400), "")
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19500, true), "")
                // re-enable node3
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19600), "")
                .postTransactionUntilConfirmed("Propose different system anchoring chain configs #2")

        voteOnAllProposals(node2.provider)
        voteOnAllProposals(node3.provider)

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData, node3.pubkey.wData),
                    getLastBlockConfigSigners(node1, systemAnchoringBrid).toSet())
        }
        awaitQueryResult {
            assertEquals(
                    setOf(500, 19200, 19400, 19600),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, systemAnchoringBrid))
        }
    }

    @Test
    @Order(7)
    fun `Reconfigure anchoring chain by faulty-remove-signer-config`() {
        testLogger.info("Reconfigure CAC by faulty-remove-signer-config")

        val pcuVs = "pcu_vs"
        val pcuCluster = "pcu_cluster"

        // 1. Create a new cluster `pcu_cluster`
        node1.c0.transactionBuilder()
                .createVoterSetOperation(node1.providerPubkey, pcuVs, 1, listOf(node1.providerPubkey), null)
                .createClusterOperation(node1.providerPubkey, pcuCluster, pcuVs, listOf(node1.providerPubkey))
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, pcuCluster)
                .postTransactionUntilConfirmed("Create voterset $pcuVs, cluster $pcuCluster with provider1/node1")

        awaitQueryResult {
            Assertions.assertNotNull(node1.c0.getClusters().find { it.name == pcuCluster })
            val chains = node1.c0.getClusterBlockchains(pcuCluster)
            assertEquals(1, chains.size)
            val brid = BlockchainRid(chains.first())

            // signers from cluster anchoring chain (CAC) config
            val actual = getLastBlockConfigSigners(node1, brid)
            assertEquals(setOf(node1.pubkey.wData), actual.toSet())
        }

        // 2. Add provider2/node2 to the pcu_cluster
        val cac = BlockchainRid(node1.c0.cmGetClusterInfo(pcuCluster).anchoringChain)
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .proposeClusterProviderOperation(node1.providerPubkey, pcuCluster, node2.providerPubkey, true, "")
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, pcuCluster)
                .postTransactionUntilConfirmed("Add provider2/node2 to the $pcuCluster")

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, cac).toSet())
        }

        // 3. Proposing a faulty remove signer config
        testLogger.info("Proposing faulty remove signer pending config")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider))
                // proposing faulty pending_config1 and pending_removed_signers_config2,
                // so that config2 will contain base_config1 and will fail
                .proposeConfigurationOperation(node1.providerPubkey, cac, cacConfig(20100, true), "")
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .proposeConfigurationOperation(node1.providerPubkey, cac, cacConfig(20200), "")
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs #3")

        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, cac).toSet())
        }
        awaitQueryResult {
            assertEquals(
                    setOf(500, 20200),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, cac))
        }
    }

    private fun cacConfig(param: Int, faulty: Boolean = false) = GtvEncoder.encodeGtv(compileDapp("cluster_anchoring", param, faulty = faulty))

    private fun sacConfig(param: Int, faulty: Boolean = false) = GtvEncoder.encodeGtv(compileDapp("system_anchoring", param, faulty = faulty))

}
