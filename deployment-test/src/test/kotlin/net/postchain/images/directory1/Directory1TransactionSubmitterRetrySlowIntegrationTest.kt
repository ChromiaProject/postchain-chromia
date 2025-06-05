package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.contracts.DirectoryChainValidator
import net.postchain.eif.contracts.ManagedValidator
import net.postchain.eif.transaction_submitter.TransactionStatus
import net.postchain.eif.transaction_submitter.getEvmSubmitTransactionNodeFailures
import net.postchain.eif.transaction_submitter.getEvmTransactionStatus
import net.postchain.eif.transaction_submitter.getTransactionTakenBy
import net.postchain.eif.transaction_submitter.getTransactions
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.tx.Contract
import java.util.concurrent.TimeUnit
import net.postchain.eif.transaction_submitter.getTransaction as getEvmTransaction

/**
 * This test will verify the TXS retry logic by letting 3 nodes fail to submit a tx and then start up a 4th node which
 * will succeed.
 *
 * 1. 4 providers registered and 3 nodes started with failing rpc urls.
 * 2. A transaction is submitted which will fail on all 3 nodes, but it will not fail completely since there is
 *    1 provider left to attempt submitting it.
 * 3. Node 2 & 3 is restarted with valid rpc urls to help out with verification.
 * 4. Node 4 is started with valid rpc urls and will pick up the transaction and submit it successfully.
 * 5. Node 2, 3 and 4 will verify the transaction and set it to SUCCESSFUL. Node 1 will keep rejecting it.
 */
@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1TransactionSubmitterRetrySlowIntegrationTest : EvmTestBase("TxsRetry_EvmContainerLogger") {

    private val node1Logger = KotlinLogging.logger("TxsRetry_Node1Logger")
    private val node2Logger = KotlinLogging.logger("TxsRetry_Node2Logger")
    private val node3Logger = KotlinLogging.logger("TxsRetry_Node3Logger")
    private val node4Logger = KotlinLogging.logger("TxsRetry_Node4Logger")
    override val logsSubdir = "txs_retry"

    private lateinit var directoryChainValidator: DirectoryChainValidator
    private lateinit var validator: ManagedValidator

    private val directoryChainValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/DirectoryChainValidator.sol/DirectoryChainValidator.json")
    private val managedValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/ManagedValidator.sol/ManagedValidator.json")

    private lateinit var txsClient: PostchainClient
    private var txId: Long = -1

    init {

        // Nodes
        chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()

        node1 = createTxsPostchainContainer("node1", node1Logger.underlyingLogger, provider1KeyPair)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", "http://localhost:1")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_EVM_HEALTHCHECK_INTERVAL", "-1")

        node2 = createTxsPostchainContainer("node2", node2Logger.underlyingLogger, provider2KeyPair)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", "http://localhost:1")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_EVM_HEALTHCHECK_INTERVAL", "-1")
                .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())

        node3 = createTxsPostchainContainer("node3", node3Logger.underlyingLogger, provider3KeyPair)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", "http://localhost:1")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_EVM_HEALTHCHECK_INTERVAL", "-1")
                .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    private fun createTxsPostchainContainer(hostname: String, logger: org.slf4j.Logger, keyPair: KeyPair): PostchainContainer {
        return postchainServer(hostname, Slf4jLogConsumer(logger, true),
                keyPair,
                "config-no-subnodes")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")
    }

    @Test
    @Order(1)
    fun `Setup the network`() {
        testLogger.info("Setup the network")
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

        testLogger.info("Adding system providers provider2-4 and node2-3")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "https://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "https://provider3.com"),
                ProviderInfo(provider4KeyPair.pubKey.wData, "provider4", "https://provider4.com")
        )

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider, provider4KeyPair)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(provider4KeyPair.pubKey.data, "03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4".hexStringToByteArray(), "node4", 1, "http://not-running:1", listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System providers 2-4 and node2-3 added to the system cluster")

        assertNumberOfChainSigners(chain0Brid, 4)
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
                .replace("VALIDATOR_CONTRACT_VALUE", validator.contractAddress.substring(2))
                .replace("<string>SUPERMAJORITY</string>", "<string>ALL</string>")
                .replace("<entry key=\"tx_node_submit_timeout\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"tx_node_submit_timeout\"><int>20000</int>")
                .replace("<entry key=\"tx_verification_timeout\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"tx_verification_timeout\"><int>120000</int>")
                .replace("<entry key=\"tx_timeout\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"tx_timeout\"><int>300000</int>")
                .replace("<entry key=\"maxblocktime\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"maxblocktime\"><int>1000</int>")
                .replace("<entry key=\"node_tx_verification_evm_blocks\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"node_tx_verification_evm_blocks\"><int>2</int>")
                .replace("<entry key=\"tx_verification_time\">\\s*<int>[0-9]*</int>".toRegex(), "<entry key=\"tx_verification_time\"><int>10000</int>")
        val evmTxSubmitterChainGtvConfig = GtvMLParser.parseGtvML(xml)

        node1.c0.transactionBuilder()
                .initEvmTransactionSubmitterChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(evmTxSubmitterChainGtvConfig))
                .postTransactionUntilConfirmed("Add $EVM_TX_SUBMITTER_CHAIN")

        val evmTransactionSubmitterChainRid = getEvmTransactionSubmitterChainRid(node1)
        val txSubmitterBrid = BlockchainRid(evmTransactionSubmitterChainRid)
        txsClient = node1.client(txSubmitterBrid, listOf(provider1KeyPair))

        testLogger.info { "$EVM_TX_SUBMITTER_CHAIN deployed: $txSubmitterBrid" }

        assertNumberOfChainSigners(txSubmitterBrid, 4)
    }

    /**
     * At this point, we have 3 nodes running (node4 is not started yet). We expect the transaction to fail on all of
     * these nodes but not fail completely since our transaction strategy is to let all nodes try to submit it
     * before it fails.
     */
    @Test
    @Order(4)
    fun `Verify anchoring transaction is failed by all 3 nodes`() {

        testLogger.info("Verify anchoring is failed by all 3 nodes")

        txId = Awaitility.await().atMost(1, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilNotNull {

            testLogger.info("Waiting for transaction to be created")

            val transactions = txsClient.getTransactions(null, null, null, null, null, null, count = 50L)

            if (transactions.size == 1) transactions[0].rowId else null
        }

        Awaitility.await().atMost(2, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {

            testLogger.info("Waiting for tx to be taken and failed by all 3 nodes")

            val takenBy = txsClient.getTransactionTakenBy(txId)
            takenBy.forEach {
                testLogger.info { " Taken by ${it.toHex()}" }
            }

            assertThat(takenBy.size).isEqualTo(3)
        }

        Awaitility.await().atMost(1, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {

            testLogger.info("Waiting tx go back to status QUEUED")

            val status = txsClient.getEvmTransactionStatus(txId)

            testLogger.info { "TX status: $status" }

            assertThat(status).isNotNull()
            assertThat(status).isEqualTo(TransactionStatus.QUEUED)

            val evmSubmitTransactionNodeFailures = txsClient.getEvmSubmitTransactionNodeFailures(txId)
            assertThat(evmSubmitTransactionNodeFailures).hasSize(3)
            assertThat(evmSubmitTransactionNodeFailures.all { it.reason.startsWith("Failed to get gas estimate") }).isTrue()
        }

        testLogger.info("TX has failed by 3 nodes and is not ready to be picked up by node4 when started")
    }

    /**
     * Before starting node4 we restart node2-3 with valid rpc urls which will help verify the transaction when node4
     * is started and has submitted it.
     */
    @Test
    @Order(5)
    fun `Restart node 2-3 with valid rpc url to be able to verify tx once submitted by node 4`() {

        testLogger.info("Restart node2-3 with valid rpc endpoints")

        node2 = restartNode(node2) {
            createTxsPostchainContainer("node2", node2Logger.underlyingLogger,
                    provider2KeyPair)
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
        }

        node3 = restartNode(node3) {
            createTxsPostchainContainer("node3", node3Logger.underlyingLogger, provider3KeyPair)
                    .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
        }
    }

    /**
     * The state is now:
     * node1    Invalid rpc - will reject all transactions
     * node2/3  Valid rpc, will not pickup the transaction since they already tried it, but will help verify submitted
     *          transaction
     * node4    About to be started and then take and submit the transaction and verify it
     */
    @Test
    @Order(6)
    fun `Start node4 and verify it successfully submits the transaction`() {

        testLogger.info("Starting the 4th node with valid rpc url")

        node4 = createTxsPostchainContainer("node4", node4Logger.underlyingLogger, provider4KeyPair)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_EVM_HEALTHCHECK_INTERVAL", "-1")
                .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())

        node4.start()
        startBlockchain(node4.channel, chain0Config)

        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {

            val status = txsClient.getEvmTransactionStatus(txId)

            testLogger.info { "TX status: $status" }

            assertThat(status).isNotNull()
            assertThat(status).isEqualTo(TransactionStatus.SUCCESS)
        }

        val txHash = txsClient.getEvmTransaction(txId).txHash

        val receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
        assertThat(receiptOpt.isPresent).isTrue()

        testLogger.info("TX is SUCCESS and confirmed on EVM")
    }

    private fun getEvmTransactionSubmitterChainRid(node1: PostchainContainer): ByteArray {

        return Awaitility.await().atMost(2, TimeUnit.MINUTES).pollInterval(Duration.ONE_HUNDRED_MILLISECONDS).untilNotNull {
            node1.c0.getEvmTransactionSubmitterChainRid()
        }
    }
}