package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.economy_chain_in_directory_chain.getEconomyChainRid
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_container.proposeContainerOperation
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
import org.awaitility.Awaitility
import org.awaitility.Duration
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
import org.web3j.tx.Contract
import java.util.concurrent.TimeUnit

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1TransactionSubmitterSlowIntegrationTest : EvmTestBase("EvmTxs_EvmContainerLogger") {

    private val node1Logger = KotlinLogging.logger("EvmTxs_Node1Logger")
    private val node2Logger = KotlinLogging.logger("EvmTxs_Node2Logger")
    private val node3Logger = KotlinLogging.logger("EvmTxs_Node3Logger")
    override val logsSubdir = "evm_tx_submitter"

    private val dappContainer = "dappContainer"
    private val dappContainerVoterSet = "dappContainer_vs"

    private lateinit var directoryChainValidator: DirectoryChainValidator
    private lateinit var anchoring: Anchoring
    private lateinit var validator: ManagedValidator
    private lateinit var ecValidator: ManagedValidator

    private val directoryChainValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/DirectoryChainValidator.sol/DirectoryChainValidator.json")
    private val managedValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/ManagedValidator.sol/ManagedValidator.json")
    private val anchoringBinary = getBinaryFromArtifactResource("/artifacts/contracts/anchoring/Anchoring.sol/Anchoring.json")

    private lateinit var txsClient: PostchainClient
    private lateinit var txSubmitterBrid: BlockchainRid

    init {
        // Nodes
        chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
        node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                provider1KeyPair,
                "config-mix")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("ANCHORING_CHECK_CLUSTER_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_SYSTEM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_RPC_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("ANCHORING_CHECK_EVM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_ANCHORING_CONTRACT_ADDRESS", "0x679170cc953b01d270349a344c4ed5634344ca04")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

        node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                provider2KeyPair,
                "config-mix")
                .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("ANCHORING_CHECK_CLUSTER_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_SYSTEM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_RPC_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("ANCHORING_CHECK_EVM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_ANCHORING_CONTRACT_ADDRESS", "0x679170cc953b01d270349a344c4ed5634344ca04")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

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
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")
                .withEnv("ANCHORING_CHECK_CLUSTER_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_SYSTEM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_RPC_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("ANCHORING_CHECK_EVM_ANCHOR_CHECK_INTERVAL_MS", "1000")
                .withEnv("ANCHORING_CHECK_ANCHORING_CONTRACT_ADDRESS", "0x679170cc953b01d270349a344c4ed5634344ca04")

        removeSubnodeContainers()
        startNodesAndChain0()
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
                    .postTransactionUntilConfirmed("init")
            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
            assertAnchoringChainProperties()

            // We pretend that this is EC just to be able to test system chain bridges
            val configGtv = compileDapp("test_dapp3")
            transactionBuilder()
                    .initEconomyChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(configGtv))
                    .postTransactionUntilConfirmed("Added mocked economy chain")
            ecBrid = BlockchainRid(node1.c0.getEconomyChainRid()!!)
        }
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

        // Deploy mocked validator contract
        ecValidator = Contract.deployRemoteCall(ManagedValidator::class.java, web3j, transactionManager, gasProvider, managedValidatorBinary, encodedValidatorConstructor).send()
        ecValidator.setBlockchainRid(Bytes32(ecBrid.data)).send()
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
        txSubmitterBrid = BlockchainRid(evmTransactionSubmitterChainRid!!)
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
    fun `Verify system bridge can be added dynamically`() {
        val xml = this::class.java.getResource("/directory1deployment/transaction_submitter_with_system_bridge.xml")!!
                .readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", directoryChainValidator.contractAddress.substring(2))
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
                .replace("x\"SYSTEM_ANCHORING_CHAIN_BRID_VALUE\"", systemAnchoringBrid.toHex())
                .replace("ANCHORING_CONTRACT_VALUE", anchoring.contractAddress.substring(2))
                .replace("VALIDATOR_CONTRACT_VALUE", validator.contractAddress.substring(2))
                .replace("x\"SYSTEM_CHAIN_BRID_VALUE\"", ecBrid.toHex())
                .replace("SYSTEM_VALIDATOR_CONTRACT", ecValidator.contractAddress.substring(2))
        val evmTxSubmitterChainGtvConfig = GtvMLParser.parseGtvML(xml)

        node1.c0.transactionBuilder()
                .proposeConfigurationOperation(node1.providerPubkey, txSubmitterBrid, GtvEncoder.encodeGtv(evmTxSubmitterChainGtvConfig), "", null)
                .postTransactionUntilConfirmed("Add new system chain bridge")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        // Assert that signer update transaction is submitted successfully
        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {
            val latestSignerListUpdateTxs = txsClient.latestSignerListUpdateTxs(ecBrid)
            testLogger.info { "Waiting for signer update transaction - ${latestSignerListUpdateTxs?.status}" }
            assertThat(latestSignerListUpdateTxs?.status).isNotNull().isEqualTo(SignerListUpdateStatus.COMPLETED)
        }
    }

    // In order to verify that anchoring check works on both subnode and non-subnode chains we start a dapp in a container
    @Test
    @Order(7)
    fun `Launch new dApp`() {
        testLogger.info { "Add node3 with subnode arch" }
        val newProviders = listOf(
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .postTransactionUntilConfirmed("System provider3 registered")

        voteOnAllProposals(listOf(node2.provider))

        node1.client(chain0Brid, listOf(node3.provider)).transactionBuilder().addNop()
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("node3 added to the system cluster")

        testLogger.info { "Create dApp container" }
        node1.c0.transactionBuilder()
                .createVoterSetOperation(node1.providerPubkey, dappContainerVoterSet, 1, listOf(node1.providerPubkey), "SYSTEM_P")
                .proposeContainerOperation(
                        node1.providerPubkey, systemCluster, dappContainer, dappContainerVoterSet, ""
                )
                .postTransactionUntilConfirmed("$dappContainer container created")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        awaitUntilAsserted {
            assertThat(node1.c0.getContainers().map { it.name }).contains(dappContainer)
        }

        testLogger.info { "Deploy dApp" }
        deployDapp("test_dapp", dappContainer)

        nodes().forEach { node ->
            assertThat(node.c0.getBlockchains(true).size).isEqualTo(6)
        }

        assertThatDappProcessesTx(dapps["test_dapp"]!!, "add_city", "Heraklion", "get_cities")
    }

    @Test
    @Order(8)
    fun `Verify anchored block heights`() {
        nodes().forEach { node ->
            val dappClient = node.client(dapps["test_dapp"]!!)
            awaitUntilAsserted {
                val highestBlockHeightAnchoringCheck = dappClient.getHighestBlockHeightAnchoringCheck()
                testLogger.info { "highestBlockHeightAnchoringCheck: $highestBlockHeightAnchoringCheck, node: ${node.pubkey}" }

                assertThat(highestBlockHeightAnchoringCheck.cac).isNotNull()
                val cac = highestBlockHeightAnchoringCheck.cac!!
                assertThat(highestBlockHeightAnchoringCheck.sac).isNotNull()
                val sac = highestBlockHeightAnchoringCheck.sac!!
                assertThat(highestBlockHeightAnchoringCheck.evm).isNotNull()
                val evm = highestBlockHeightAnchoringCheck.evm!!

                assertThat(cac.error).isNull()
                assertTrue(cac.match!!)
                assertTrue(cac.height!! > 0)
                assertThat(sac.error).isNull()
                assertTrue(sac.match!!)
                assertTrue(sac.height!! > 0)
                assertThat(evm.error).isNull()
                assertTrue(evm.match!!)
                assertTrue(evm.height!! > 0)
            }
        }
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

            txsClient.getTransactions(null, null, null, null, null, null, count = 50L).forEach {
                testLogger.info { "TX Submitter transaction: ${it.rowId} - ${it.status} - ${it.functionName} - ${it.processedBy.toHex()}" }
            }

            if (anchoredHeight > 0) {
                anchoredHeights.add(anchoredHeight)
            }

            testLogger.info("Verifying anchoring. Current - Height: ${node1.c0.currentBlockHeight()}, TX Submitter Height: " +
                    "${txsClient.currentBlockHeight()}, EVM: $anchoredHeight, signers. ${currentEvmSignerList.joinToString(", ") { it.toHex() }}, " +
                    "anchored heights seen: $anchoredHeights")

            assertThat(currentEvmSignerList.size).isEqualTo(signers.size)
            assertThat(anchoredHeights.size).isGreaterThanOrEqualTo(awaitAnchoredHeights)
        }

        Awaitility.await().atMost(3, TimeUnit.MINUTES).untilAsserted {

            testLogger.info { "Make sure at least 1 tx is verified..." }

            assertThat(txsClient.getTransactions(null, null, null, null, null, null, count = 50L).count { it.status == TransactionStatus.SUCCESS }).isGreaterThanOrEqualTo(1)
        }
    }
}