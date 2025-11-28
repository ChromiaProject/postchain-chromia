package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_provider.proposeProviderStateOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.contracts.DirectoryChainValidator
import net.postchain.eif.contracts.ManagedValidator
import net.postchain.eif.transaction_submitter.signer_update.SignerListUpdateStatus
import net.postchain.eif.transaction_submitter.signer_update.latestSignerListUpdateTxs
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.tx.Contract
import java.util.concurrent.TimeUnit

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1TransactionSubmitterHistoricalUpdateSlowIntegrationTest : EvmTestBase("txs_historical") {

    private lateinit var directoryChainValidator: DirectoryChainValidator
    private lateinit var validator: ManagedValidator

    private val directoryChainValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/DirectoryChainValidator.sol/DirectoryChainValidator.json")
    private val managedValidatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/validatorupdate/ManagedValidator.sol/ManagedValidator.json")

    private lateinit var txsClient: PostchainClient
    private lateinit var txSubmitterBrid: BlockchainRid

    init {
        // Nodes
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-no-subnodes")
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
                .withEnv("POSTCHAIN_TRANSACTION_SUBMITTER_ETHEREUM_PRIVATE_KEY", "0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")

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

            testLogger.info { "Adding new cluster with container and dApp chain..." }
            // Let's add a new cluster and a new chain in a container
            transactionBuilder(listOf(node1.provider, node2.provider))
                    .registerProviderOperation(node1.providerPubkey, node2.provider.pubKey, ProviderTier.NODE_PROVIDER)
                    .proposeProviderStateOperation(node1.providerPubkey, node2.providerPubkey, true, "")
                    .createClusterOperation(node1.providerPubkey, "cluster1", "SYSTEM_P", listOf(node2.providerPubkey))
                    .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf("cluster1"), 2)
                    .createContainerOperation(node1.providerPubkey, "container1", "cluster1", 1, listOf(node1.providerPubkey))
                    .postTransactionUntilConfirmed("Cluster and container created")

            deployDapp("test_dapp", "container1", assertSigners = arrayOf(node2))
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
        validator.setBlockchainRid(Bytes32(dapps["test_dapp"]!!.data)).send()
    }

    @Test
    @Order(3)
    fun `Add tx submitter`() {
        testLogger.info("Adding tx submitter chain")

        val xml = this::class.java.getResource("/directory1deployment/transaction_submitter_without_anchoring.xml")!!
                .readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", directoryChainValidator.contractAddress.substring(2))
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
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
    fun `Perform signer update in system cluster`() {
        // We do this so that the latest signer update for DC will be after the latest update for the dApp chain
        testLogger.info("Add system provider provider3 and its node")
        val newProviders = listOf(
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )
        node1.client(chain0Brid, listOf(node1.provider, node3.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System provider3 registered, node3 added to the system cluster")

        // Wait for the signer update transaction to be processed
        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {
            val latestSignerListUpdateTxs = txsClient.latestSignerListUpdateTxs(chain0Brid)
            testLogger.info { "Waiting for signer update transaction - ${latestSignerListUpdateTxs?.status}" }
            assertThat(latestSignerListUpdateTxs?.status).isNotNull().isEqualTo(SignerListUpdateStatus.COMPLETED)
        }
    }

    @Test
    @Order(5)
    fun `Verify system bridge that requires historical update can be added`() {
        testLogger.info("Updating tx submitter chain to trigger historical signer update")

        val xml = this::class.java.getResource("/directory1deployment/transaction_submitter_without_anchoring_with_system_bridge.xml")!!
                .readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", directoryChainValidator.contractAddress.substring(2))
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
                .replace("x\"SYSTEM_CHAIN_BRID_VALUE\"", dapps["test_dapp"]!!.toHex())
                .replace("SYSTEM_VALIDATOR_CONTRACT", validator.contractAddress.substring(2))
        val evmTxSubmitterChainGtvConfig = GtvMLParser.parseGtvML(xml)

        node1.c0.transactionBuilder()
                .proposeConfigurationOperation(node1.providerPubkey, txSubmitterBrid, GtvEncoder.encodeGtv(evmTxSubmitterChainGtvConfig), "", null)
                .postTransactionUntilConfirmed("Add new system chain bridge")

        voteOnAllProposals(listOf(node3.provider))

        // Assert that historical signer update transaction is submitted successfully
        Awaitility.await().atMost(3, TimeUnit.MINUTES).pollInterval(Duration.TWO_SECONDS).untilAsserted {
            val latestSignerListUpdateTxs = txsClient.latestSignerListUpdateTxs(dapps["test_dapp"]!!)
            testLogger.info { "Waiting for signer update transaction - ${latestSignerListUpdateTxs?.status}" }
            assertThat(latestSignerListUpdateTxs?.status).isNotNull().isEqualTo(SignerListUpdateStatus.COMPLETED)
        }
    }
}
