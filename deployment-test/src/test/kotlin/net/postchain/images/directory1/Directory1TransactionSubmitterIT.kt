package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.compressKey
import net.postchain.eif.contracts.Anchoring
import net.postchain.eif.contracts.DirectoryChainValidator
import net.postchain.eif.contracts.ManagedValidator
import net.postchain.eif.getEthereumAddress
import net.postchain.eif.transaction_submitter.TransactionStatus
import net.postchain.eif.transaction_submitter.getTransactions
import net.postchain.eif.transaction_submitter.signer_update.SignerListUpdateStatus
import net.postchain.eif.transaction_submitter.signer_update.getCurrentEvmSignerList
import net.postchain.eif.transaction_submitter.signer_update.latestSignerListUpdateTxs
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.util.concurrent.TimeUnit

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1TransactionSubmitterIT {

    companion object : ManagedModeBase() {

        private const val EVM_TX_SUBMITTER_CHAIN = "evm_transaction_submitter_chain"

        private val evmContainerLogger = KotlinLogging.logger("EvmTxs_EvmContainerLogger")
        private val node1Logger = KotlinLogging.logger("EvmTxs_Node1Logger")
        private val node2Logger = KotlinLogging.logger("EvmTxs_Node2Logger")
        private val node3Logger = KotlinLogging.logger("EvmTxs_Node3Logger")
        override val logsSubdir = "txs"
        private val provider1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        private val evmContainer: GethContainer
        private val web3j: Web3j
        private val transactionManager: TransactionManager
        private val gasProvider = DefaultGasProvider()

        private lateinit var directoryChainValidator: DirectoryChainValidator
        private lateinit var anchoring: Anchoring
        private lateinit var validator: ManagedValidator

        private val directoryChainValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/DirectoryChainValidator.sol/DirectoryChainValidator.json")
        private val managedValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/ManagedValidator.sol/ManagedValidator.json")
        private val anchoringBinary = getBinaryFromArtifactResource("/artifacts/contracts/anchoring/Anchoring.sol/Anchoring.json")

        private lateinit var txsClient: PostchainClient

        init {
            // Initialize EVM container
            evmContainer = GethContainer(logger = Slf4jLogConsumer(evmContainerLogger.underlyingLogger, true))
                    .withNetwork(network)
                    .apply {
                        start()
                    }

            // Web3j
            web3j = Web3j.build(HttpService(evmContainer.getExternalGethUrl()))
            transactionManager = FastRawTransactionManager(
                    web3j,
                    Credentials.create("0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610"),
                    PollingTransactionReceiptProcessor(web3j, 1000, 30)
            )

            // Nodes
            chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    provider1KeyPair,
                    "config-mix")
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("ANCHORING_CHECK_RPC_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("ANCHORING_CHECK_EVM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                    .withEnv("ANCHORING_CHECK_ANCHORING_CONTRACT_ADDRESS", "0x679170cc953b01d270349a344c4ed5634344ca04")
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix")
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

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
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

            removeSubnodeContainers()
            startNodesAndChain0()
        }

        private fun getBinaryFromArtifactResource(resourcePath: String): String {
            val artifactFile = Directory1TransactionSubmitterIT::class.java.getResource(resourcePath)?.readText()
            val artifactJson = GsonBuilder().create().fromJson(artifactFile, JsonObject::class.java)
            return artifactJson.get("bytecode").asString
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            evmContainer.stop()
            super.breakdown()
        }
    }

    @Test
    @Order(1)
    fun `Setup the network`() {
        testLogger.info("Setup the network")
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
    fun `Deploy contracts on EVM`() {
        testLogger.info { "Deploy contracts on EVM" }

        // Deploy directory chain validator contract
        val encodedDirectoryValidatorConstructor = FunctionEncoder.encodeConstructor(listOf(Bytes32(chain0Brid.data)))
        directoryChainValidator = Contract.deployRemoteCall(DirectoryChainValidator::class.java, web3j, transactionManager, gasProvider, directoryChainValidatorBinary, encodedDirectoryValidatorConstructor).send()

        // Deploy validator contract
        val encodedValidatorConstructor = FunctionEncoder.encodeConstructor(listOf(Address(directoryChainValidator.contractAddress)))
        validator = Contract.deployRemoteCall(ManagedValidator::class.java, web3j, transactionManager, gasProvider, managedValidatorBinary, encodedValidatorConstructor).send()
        validator.setBlockchainRid(Bytes32(systemAnchoringBrid.data)).send()

        // Deploy anchoring contract
        val encodedAnchoringConstructor = FunctionEncoder.encodeConstructor(listOf(Address(validator.contractAddress), Bytes32(systemAnchoringBrid.data)))
        anchoring = Contract.deployRemoteCall(Anchoring::class.java, web3j, transactionManager, gasProvider, anchoringBinary, encodedAnchoringConstructor).send()
    }

    @Test
    @Order(3)
    fun `Add tx submitter`() {

        testLogger.info("Adding tx submitter chain")

        val xml = this::class.java.getResource("/directory1deployment/transaction_submitter.xml")!!
                .readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", directoryChainValidator.contractAddress.substring(2))
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
                .replace("x\"SYSTEM_ANCHORING_CHAIN_BRID_VALUE\"", systemAnchoringBrid.toHex())
                .replace("ANCHORING_CONTRACT_VALUE", anchoring.contractAddress.substring(2))
                .replace("VALIDATOR_CONTRACT_VALUE", validator.contractAddress.substring(2))
        val evmTxSubmitterChainGtvConfig = GtvMLParser.parseGtvML(xml)

        node1.c0.transactionBuilder()
                .initEvmTransactionSubmitterChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(evmTxSubmitterChainGtvConfig))
                .postTransactionUntilConfirmed("Add $EVM_TX_SUBMITTER_CHAIN")

        val evmTransactionSubmitterChainRid = node1.c0.getEvmTransactionSubmitterChainRid()
        val txSubmitterBrid = BlockchainRid(evmTransactionSubmitterChainRid!!)
        txsClient = node1.client(txSubmitterBrid, listOf(provider1KeyPair))

        testLogger.info { "$EVM_TX_SUBMITTER_CHAIN deployed: $txSubmitterBrid" }
    }

    @Test
    @Order(4)
    fun `Verify anchoring - one provider`() {

        testLogger.info("Verify anchoring - one provider")

        assertAnchoringInProgress(listOf(node1.pubkey))
    }

    @Test
    @Order(5)
    fun `Verify anchoring - two providers`() {

        testLogger.info("Verify anchoring - two provider2")

        // Adding provider2 as system
        testLogger.info("Add system provider provider2 its node")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com")
        )
        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System provider2 registered, node2 added to the system cluster")

        // Wait for the signer update transaction to be processed
        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {
            val latestSignerListUpdateTxs = txsClient.latestSignerListUpdateTxs(systemAnchoringBrid)
            testLogger.info { "Waiting for signer update transaction - ${latestSignerListUpdateTxs?.status}" }
            assertThat(latestSignerListUpdateTxs?.status).isNotNull().isEqualTo(SignerListUpdateStatus.COMPLETED)
        }

        assertAnchoringInProgress(listOf(node1.pubkey, node2.pubkey))
    }

    @Test
    @Order(6)
    fun `Verify anchored block heights`() {
        val highestBlockHeightAnchoringCheck = node1.c0.getHighestBlockHeightAnchoringCheck()
        val cac = highestBlockHeightAnchoringCheck.cac!!
        val sac = highestBlockHeightAnchoringCheck.sac!!
        val evm = highestBlockHeightAnchoringCheck.evm!!

        testLogger.info { "highestBlockHeightAnchoringCheck: $highestBlockHeightAnchoringCheck" }

        assertTrue(cac.match!!)
        assertTrue(cac.height!! > 0)
        assertTrue(sac.match!!)
        assertTrue(sac.height!! > 0)
        assertTrue(evm.match!!)
        assertTrue(evm.height!! > 0)
    }

    private fun assertAnchoringInProgress(signers: List<PubKey>, awaitAnchoredHeights: Int = 3) {

        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {

            testLogger.info { "Verifying validators on EVM side..." }

            // Make sure all providers are set as validators on EVM side
            for (signer in signers) {
                assertThat(validator.isValidator(Address(getEthereumAddress(compressKey(signer.data)).toHex())).send().value).isNotNull().isTrue()
            }
        }

        val anchoredHeights = mutableSetOf<Int>()
        Awaitility.await().atMost(3, TimeUnit.MINUTES).untilAsserted {

            val anchoredHeight = anchoring.lastAnchoredHeight().send().value.intValueExact()
            val currentEvmSignerList = txsClient.getCurrentEvmSignerList(systemAnchoringBrid)

            txsClient.getTransactions().forEach {
                testLogger.info { "TX Submitter transaction: ${it.rowId} - ${it.status} - ${it.functionName} - ${it.processedBy.toHex()}" }
            }

            if (anchoredHeight > 0) {
                anchoredHeights.add(anchoredHeight)
            }

            testLogger.info("Verifying anchoring. Current - Height: ${node1.c0.currentBlockHeight()}, TX Submitter Height: " +
                    "${txsClient.currentBlockHeight()}, EVM: $anchoredHeight, signers. ${currentEvmSignerList.joinToString(", ") { it.toHex() }}, " +
                    "anchored heights seen: ${anchoredHeights}")

            assertThat(currentEvmSignerList.size).isEqualTo(signers.size)
            assertThat(anchoredHeights.size).isGreaterThanOrEqualTo(awaitAnchoredHeights)
        }

        Awaitility.await().atMost(3, TimeUnit.MINUTES).untilAsserted {

            testLogger.info { "Make sure at least 1 tx is verified..." }

            assertThat(txsClient.getTransactions().count { it.status == TransactionStatus.SUCCESS }).isGreaterThanOrEqualTo(1)
        }
    }
}