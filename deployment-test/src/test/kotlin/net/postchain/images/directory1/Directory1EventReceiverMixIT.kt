package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.common.queries.getVoterSets
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
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
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1EventReceiverMixIT {

    companion object : ManagedModeBase() {

        private const val EVM_EVENT_RECEIVER_CHAIN_NAME = "evm_event_receiver_chain"
        private const val PROVIDER1_VS = "provider1_vs"
        private const val PROVIDER2_VS = "provider2_vs"

        private val node1Logger = KotlinLogging.logger("EvmEventReceiver_Node1Logger")
        private val node2Logger = KotlinLogging.logger("EvmEventReceiver_Node2Logger")
        private val node3Logger = KotlinLogging.logger("EvmEventReceiver_Node3Logger")
        override val logsSubdir = "evm_event_receiver"
        private val node1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        lateinit var eventReceiverBrid: BlockchainRid

        init {
            chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()

            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    node1KeyPair,
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
                    .postTransactionUntilConfirmed("init")
            assertThat(getSummary().providers).isEqualTo(1L)
            assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }
        assertAnchoringChainProperties()

        // Adding provider2 as system
        testLogger.info("Adding system provider provider2 its node")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com")
        )
        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System provider2 registered, node2 added to the system cluster")

        // Creating voter sets for provider1 and provider2
        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder().addNop()
                .createVoterSetOperation(node1.providerPubkey, PROVIDER1_VS, 0, listOf(node1.providerPubkey), null)
                .createVoterSetOperation(node2.providerPubkey, PROVIDER2_VS, 0, listOf(node2.providerPubkey), null)
                .postTransactionUntilConfirmed("Voter sets $PROVIDER1_VS and $PROVIDER2_VS created")
        awaitQueryResult {
            assertThat(node1.c0.getVoterSets().map { it.name }).containsAll(PROVIDER1_VS, PROVIDER2_VS)
        }
    }

    @Test
    @Order(2)
    fun `Add EVM Event Receiver Chain`() {
        testLogger.info("Adding EVM Event Receiver Chain")
        val gtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/evm_event_receiver.xml")!!.readText())

        node1.c0.transactionBuilder()
                .initEvmEventReceiverChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add $EVM_EVENT_RECEIVER_CHAIN_NAME")

        val tcRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_EVENT_RECEIVER_CHAIN_NAME }?.rid
        assertThat(tcRid).isNotNull()
        eventReceiverBrid = BlockchainRid(tcRid!!)

        testLogger.info { "$EVM_EVENT_RECEIVER_CHAIN_NAME deployed: $eventReceiverBrid" }
    }

}