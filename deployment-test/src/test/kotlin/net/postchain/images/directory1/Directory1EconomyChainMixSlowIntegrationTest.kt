@file:Suppress("DEPRECATION")

package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.addProviderKeyOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.revokeProviderKeyOperation
import net.postchain.chain0.common.operations.setProviderKeyThresholdOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateProviderOperation
import net.postchain.chain0.common.queries.getBlockchainInfo
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainerData
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getProviderKeys
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.common.queries.getVoterSetMembers
import net.postchain.chain0.common.queries.getVoterSets
import net.postchain.chain0.economy_chain.ClusterCreationStatus
import net.postchain.chain0.economy_chain.TagData
import net.postchain.chain0.economy_chain.TicketState
import net.postchain.chain0.economy_chain.createClusterOperation
import net.postchain.chain0.economy_chain.createContainerOperation
import net.postchain.chain0.economy_chain.createTagOperation
import net.postchain.chain0.economy_chain.getBalance
import net.postchain.chain0.economy_chain.getClusterCreationStatus
import net.postchain.chain0.economy_chain.getClusters
import net.postchain.chain0.economy_chain.getCreateContainerTicketByTransaction
import net.postchain.chain0.economy_chain.getLeasesByAccount
import net.postchain.chain0.economy_chain.getPoolBalance
import net.postchain.chain0.economy_chain.getTagByName
import net.postchain.chain0.economy_chain.getUpgradeContainerTicketByTransaction
import net.postchain.chain0.economy_chain.initOperation
import net.postchain.chain0.economy_chain.registerDappProviderOperation
import net.postchain.chain0.economy_chain.transferToPoolOperation
import net.postchain.chain0.economy_chain.upgradeContainerOperation
import net.postchain.chain0.economy_chain_in_directory_chain.getEconomyChainRid
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.lib.ft4.core.accounts.AuthDescriptor
import net.postchain.chain0.lib.ft4.core.accounts.AuthType
import net.postchain.chain0.lib.ft4.external.accounts.Ft4GetAccountMainAuthDescriptorResult
import net.postchain.chain0.lib.ft4.external.accounts.getAccountMainAuthDescriptor
import net.postchain.chain0.lib.ft4.external.accounts.updateMainAuthDescriptorOperation
import net.postchain.chain0.lib.hbridge.bridgeFtAssetToEvmOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ContainerState
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_cluster.proposeClusterProviderOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.chain0.provider_auth.model.ProviderKeyRole
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.EndpointPool
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.EventMerkleProof
import net.postchain.eif.contracts.ChromiaTestToken
import net.postchain.eif.contracts.ChromiaTokenBridge
import net.postchain.eif.contracts.TokenMinterTest
import net.postchain.eif.contracts.Validator
import net.postchain.eif.lib.ft4.core.auth.Signature
import net.postchain.eif.lib.ft4.external.accounts.UPDATE_MAIN_AUTH_DESCRIPTOR
import net.postchain.eif.lib.ft4.external.assets.getAssetBalance
import net.postchain.eif.lib.ft4.external.assets.getAssetsByName
import net.postchain.eif.lib.ft4.external.auth.evmSignaturesOperation
import net.postchain.eif.lib.ft4.external.auth.ftAuthOperation
import net.postchain.eif.lib.ft4.external.auth.getAuthMessageTemplate
import net.postchain.eif.lib.hbridge.core.getEoaAddressesForAccount
import net.postchain.eif.lib.hbridge.core.linkEvmEoaAccountOperation
import net.postchain.eif.lib.hbridge.getErc20WithdrawalByTx
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.tx.Contract
import java.math.BigInteger
import java.nio.charset.StandardCharsets

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1EconomyChainMixSlowIntegrationTest : EvmTestBase("ec") {

    companion object {

        const val EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER = "EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER"
        const val EIF_EC_STRATEGY_SENDER_BLOCKCHAIN = "EIF_EC_STRATEGY_SENDER_BLOCKCHAIN"

        const val ETH_ASSET_ADDRESS = "Aa1ae68ABcd32804132370B9f73c3160dbbfC593"
        const val ETH_BRIDGE_ADDRESS = "B2ee8499B5e73795F28287A0603d66db6843AAAA"
        const val ETH_ASSET_NETWORK_ID = "11155111"
        const val ASSET_NAME = "tCHR"

        const val APP_CLUSTER1 = "appCluster1"
        const val APP_CLUSTER_TAG = "appClusterTag"
        const val CONTAINER_UNITS = 2L
        const val DURATION_WEEKS = 1L
        const val EXTRA_STORAGE_GIB = 0L
        const val SCU_PRICE = 1L
        const val EXTRA_STORAGE_PRICE = 1L
        const val EXTRA_COMPUTE_PRICE = 0L
        const val PROVIDER1_VS = "provider1_vs"
        const val PROVIDER2_VS = "provider2_vs"
        private const val DEPOSIT_NUMBER = 5
    }

    lateinit var containerName: String
    lateinit var dappBrid: BlockchainRid
    lateinit var CAC1: BlockchainRid

    // EIF
    private lateinit var validator: Validator
    private lateinit var bridge: ChromiaTokenBridge
    private lateinit var bridgeAddress: String
    private lateinit var chromiaTestToken: ChromiaTestToken
    private lateinit var minter: TokenMinterTest
    private lateinit var testTokenAddress: String
    private lateinit var eventReceiverBrid: BlockchainRid

    // EIF / balances
    private val INITIAL_SUPPLY = BigInteger.valueOf(1_000_000_000L)
    private val depositAmount = BigInteger.valueOf(1000)
    private val totalDepositedAmount = DEPOSIT_NUMBER.toBigInteger() * depositAmount
    private lateinit var assetId: ByteArray

    // EIF / users
    private lateinit var aliceAuthenticator: FTAuthenticator

    private val dappAdminKeyPair = accountCreatorKeyPair

    init {
        // Nodes
        chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-mix"
        ).withEifEnv()

        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-mix"
        )
                .withGenesisNode(node1)
                .withEifEnv()

        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-mix"
        )
                .withGenesisNode(node1)
                .withEifEnv()

        removeSubnodeContainers()
        startNodesAndChain0()
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
                    .postTransactionUntilConfirmed("init")
            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
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
    fun `Deploy contracts on EVM`() {
        testLogger.info { "Deploying contracts on EVM" }

        // Deploy validator contract
        val encodedConstructor = FunctionEncoder.encodeConstructor(listOf(DynamicArray(Address::class.java, node0EvmAddress, node1EvmAddress)))
        validator = Contract.deployRemoteCall(Validator::class.java, web3j, transactionManager, gasProvider, validatorBinary, encodedConstructor).send()

        // Deploy token bridge contract
        bridge = Contract.deployRemoteCall(ChromiaTokenBridge::class.java, web3j, transactionManager, gasProvider, chromiaTokenBridgeBinary, "").send().apply {
            initialize(Address(validator.contractAddress), Uint256(2)).send()
        }
        bridgeAddress = bridge.contractAddress

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        chromiaTestToken = Contract.deployRemoteCall(ChromiaTestToken::class.java, web3j, transactionManager, gasProvider, chromiaTestTokenBinary, "").send()
        testTokenAddress = chromiaTestToken.contractAddress
        chromiaTestToken.approve(Address(bridge.contractAddress), Uint256(INITIAL_SUPPLY)).send() // Bridge can spend the entire initial supply

        // Allow token
        bridge.allowToken(Address(chromiaTestToken.contractAddress)).send()

        // Deploy minter contract
        minter = Contract.deployRemoteCall(TokenMinterTest::class.java, web3j, transactionManager, gasProvider, tokenMinterBinary,
                FunctionEncoder.encodeConstructor(listOf(Uint(INITIAL_SUPPLY), Address(chromiaTestToken.contractAddress), Address(bridge.contractAddress), Address(aliceEvmAddressStr)))
        ).send()
        bridge.setTokenMinter(Address(minter.contractAddress)).send()
        chromiaTestToken.setTokenMinter(Address(minter.contractAddress)).send()

        val balance = chromiaTestToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(BigInteger.ZERO, balance.value)
    }

    @Test
    @Order(3)
    fun `Deploy EIF Event Receiver Chain`() {
        testLogger.info("Deploying EIF Event Receiver Chain")
        val gtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/eif_event_receiver.xml")!!
                .readText().replace(EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER, bridgeAddress)
        )

        node1.c0.transactionBuilder()
                .initEvmEventReceiverChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add $EVM_EVENT_RECEIVER_CHAIN_NAME")

        val erRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_EVENT_RECEIVER_CHAIN_NAME }?.rid
        assertThat(erRid).isNotNull()
        eventReceiverBrid = BlockchainRid(erRid!!)

        testLogger.info { "$EVM_EVENT_RECEIVER_CHAIN_NAME deployed: $eventReceiverBrid" }
    }

    @Test
    @Order(4)
    fun `Deploy Economy Chain`() {
        testLogger.info("Deploying Economy Chain")

        val senderVirtualBrid = gtv(
                gtv("EVM"),
                gtv(evmContainerNetworkId),
                gtv(testTokenAddress.substring(2).hexStringToByteArray())
        ).merkleHash(GtvMerkleHashCalculatorV2(Secp256K1CryptoSystem()))

        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!
                .readText()
                // inject Event Receiver RID
                .replace(
                        "<string>$EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER</string>",
                        "<bytea>${eventReceiverBrid.toHex()}</bytea>"
                )
                .replace(
                        "<string>$EIF_EC_STRATEGY_SENDER_BLOCKCHAIN</string>",
                        "<bytea>${senderVirtualBrid.toHex()}</bytea>"
                )
                // use `TST TestToken ERC-20` instead of `CHR Chromia ERC-20`
                .replace(ETH_ASSET_ADDRESS, testTokenAddress.substring(2))
                .replace(ETH_BRIDGE_ADDRESS, bridgeAddress.substring(2))
                .replace(ETH_ASSET_NETWORK_ID, evmContainerNetworkId.toString())
        )

        node1.c0.transactionBuilder()
                .initEconomyChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(economyChainGtvConfig))
                .postTransactionUntilConfirmed("Add $EC_CHAIN_NAME")

        val ecRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EC_CHAIN_NAME }?.rid
        assertThat(ecRid).isNotNull()
        ecBrid = BlockchainRid(ecRid!!)
        bridge.setBlockchainRid(Bytes32(ecBrid.data)).send()

        testLogger.info { "$EC_CHAIN_NAME deployed: $ecBrid" }

        node1.ec.transactionBuilder()
                .initOperation()
                .postTransactionUntilConfirmed("Init $EC_CHAIN_NAME")
        testLogger.info { "$EC_CHAIN_NAME initialized" }

        // get tCHR assetId
        assetId = awaitQueryResult {
            node1.client(ecBrid).getAssetsByName(ASSET_NAME, null, null).data[0]["id"]?.asByteArray()
        }!!
    }

    @Test
    @Order(5)
    fun `Register FT accounts`() {
        testLogger.info("Registering FT accounts")

        // Register Alice account
        aliceAuthenticator = createAccount(node1, accountCreatorKeyPair, ecBrid, aliceKeyPair, "Alice")
        linkAccount(aliceAuthenticator, aliceEvmCredentials, ecBrid)

        val aliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        testLogger.info("Alice account balance is: $aliceBalance")
        assertThat(aliceBalance).isEqualTo(INITIAL_SUPPLY)
    }

    @Test
    @Order(6)
    fun `Withdraw token to EVM`() {
        testLogger.info("Withdraw token to EVM")

        val tx = aliceAuthenticator.transactionBuilder()
                .bridgeFtAssetToEvmOperation(
                        assetId,
                        totalDepositedAmount,
                        evmContainerNetworkId,
                        aliceEvmAddress,
                        bridgeAddress.substring(2).hexStringToByteArray()
                ).postTransactionUntilConfirmed("Withdraw token to EVM")

        val withdraw = node1.ec.getErc20WithdrawalByTx(tx.txRid.rid.hexStringToByteArray(), 1)!!

        val eventProof = node1.ec.query("get_event_merkle_proof",
                gtv("eventHash" to gtv(withdraw.eventHash.toHex()))
        ).toObject<EventMerkleProof>()

        val receipt = bridge.withdrawRequest(
                eventProof.web3EventData(),
                eventProof.chromiaWeb3EventProof(),
                eventProof.web3BlockHeader(),
                eventProof.web3Signatures(),
                eventProof.web3Signers(),
                eventProof.chromiaWeb3ExtraProofData()
        ).send()

        await().atMost(Duration.TEN_SECONDS).until {
            val block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(receipt.blockNumber.add(BigInteger.TWO)), false).send()
            block.block != null
        }

        bridge.withdraw(Bytes32(withdraw.eventHash.data), Address(aliceEvmAddressStr)).send()
        // check the balance on EVM
        val aliceBalance = chromiaTestToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, totalDepositedAmount)

        // check the asset balance on Chromia
        awaitQueryResult {
            val balance = node1.client(ecBrid).getAssetBalance(aliceAuthenticator.accountId, assetId)
            assertThat(balance?.amount).isEqualTo(INITIAL_SUPPLY - totalDepositedAmount)
        }
    }

    @Test
    @Order(7)
    fun `Deposit token on EVM`() {
        testLogger.info { "Deposit token on EVM" }

        // deposit on EVM
        repeat(DEPOSIT_NUMBER) {
            bridge.deposit(Address(chromiaTestToken.contractAddress), Uint256(depositAmount)).send()
        }
        // check the balance on EVM
        val aliceBalance = chromiaTestToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, BigInteger.ZERO)

        // check the asset balance on Chromia
        awaitQueryResult {
            val balance = node1.client(ecBrid).getAssetBalance(aliceAuthenticator.accountId, assetId)
            assertThat(balance?.amount).isEqualTo(INITIAL_SUPPLY)
        }
    }

    @Test
    @Order(8)
    fun `Test pool account`() {
        testLogger.info("Test pool account")
        val poolBalance = node1.ec.getPoolBalance()
        testLogger.info("poolBalance is: $poolBalance")
        assertThat(poolBalance).isEqualTo(BigInteger.ZERO)

        // Transfer funds to the pool account
        aliceAuthenticator.verifyOperationAuthFlags("transfer_to_pool")
        aliceAuthenticator.transactionBuilder()
                .transferToPoolOperation(depositAmount)
                .postTransactionUntilConfirmed("Transfer to pool")

        val balance = aliceAuthenticator.client.getPoolBalance()
        testLogger.info("poolBalance after transfer is: $balance")
        assertThat(balance).isEqualTo(depositAmount)
    }

    @Test
    @Order(9)
    fun `Add new tag`() {
        testLogger.info("Adding tag")
        with(node1.ec) {
            transactionBuilder()
                    .createTagOperation(node1.providerPubkey, APP_CLUSTER_TAG, SCU_PRICE, EXTRA_STORAGE_PRICE, EXTRA_COMPUTE_PRICE)
                    .postTransactionUntilConfirmed("$APP_CLUSTER_TAG tag created")

            makeVoteOnLatestProposal(node2)

            assertThat(getTagByName(APP_CLUSTER_TAG))
                    .isEqualTo(TagData(APP_CLUSTER_TAG, SCU_PRICE, EXTRA_STORAGE_PRICE, EXTRA_COMPUTE_PRICE))
        }
    }

    @Test
    @Order(10)
    fun `Add cluster`() {
        testLogger.info("Adding cluster")

        with(node1.ec) {
            transactionBuilder()
                    .createClusterOperation(node1.providerPubkey, APP_CLUSTER1, "SYSTEM_P", PROVIDER1_VS, CONTAINER_UNITS, EXTRA_STORAGE_GIB, APP_CLUSTER_TAG, 50, 2048, 25, 20, 16384, 4, Long.MAX_VALUE)
                    .postTransactionUntilConfirmed("$APP_CLUSTER1 cluster created")

            // Approve APP_CLUSTER1
            makeVoteOnLatestProposal(node2)

            awaitQueryResult {
                assertThat(getClusters().map { it.name }).contains(APP_CLUSTER1)
                assertThat(getClusterCreationStatus(APP_CLUSTER1)).isEqualTo(ClusterCreationStatus.SUCCESS)
            }
        }

        // Add node1 to appCluster1
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, APP_CLUSTER1)
                .postTransactionUntilConfirmed("$APP_CLUSTER1 cluster created")

        // Add provider2 and node2 to appCluster1
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder().addNop()
                .proposeClusterProviderOperation(node1.providerPubkey, APP_CLUSTER1, node2.providerPubkey, true, "")
                .postTransactionUntilConfirmed("provider2 added to $APP_CLUSTER1")
        voteOnAllProposals(listOf(node2.provider))
        node1.client(chain0Brid, listOf(node2.provider)).transactionBuilder().addNop()
                .updateNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, APP_CLUSTER1)
                .postTransactionUntilConfirmed("Add node2 to $APP_CLUSTER1 proposal submitted")

        // Verify cluster info
        val clusterInfo = node1.c0.cmGetClusterInfo(APP_CLUSTER1)
        assertThat(clusterInfo.peers.map { it.pubkey }.toSet()).isEqualTo(setOf(node1.pubkey.wData, node2.pubkey.wData))
        CAC1 = BlockchainRid(clusterInfo.anchoringChain)
    }

    @Test
    @Order(11)
    fun `Create container`() {
        testLogger.info("Create container")
        aliceAuthenticator.verifyOperationAuthFlags("create_container")
        val tcRid = aliceAuthenticator.transactionBuilder()
                .createContainerOperation(
                        node1.provider.pubKey.data, CONTAINER_UNITS, DURATION_WEEKS, EXTRA_STORAGE_GIB, APP_CLUSTER1, true)
                .postTransactionUntilConfirmed("Create Container")
                .txRid

        awaitUntilAsserted {
            val ticket = aliceAuthenticator.client.getCreateContainerTicketByTransaction(tcRid.rid.hexStringToByteArray())
            assertThat(ticket).isNotNull()
            assertThat(ticket!!.state).isEqualTo(TicketState.SUCCESS)
        }

        val leaseDataList = aliceAuthenticator.client.getLeasesByAccount(aliceAuthenticator.accountId)
        assertThat(leaseDataList.size).isEqualTo(1)
        val leaseData = leaseDataList[0]
        assertThat(leaseData.clusterName).isEqualTo(APP_CLUSTER1)
        assertThat(leaseData.containerUnits).isEqualTo(CONTAINER_UNITS)
        assertThat(leaseData.extraStorageGib).isEqualTo(EXTRA_STORAGE_GIB)
        assertThat(leaseData.expired).isFalse()
        assertThat(leaseData.autoRenew).isTrue()

        containerName = leaseData.containerName
        val containerData = node1.c0.getContainerData(leaseData.containerName)
        assertThat(containerData).isNotNull()
        assertThat(containerData.cluster).isEqualTo(APP_CLUSTER1)
        assertThat(containerData.proposedByPubkey).isEqualTo(provider1KeyPair.pubKey.wData)
        assertThat(containerData.state).isEqualTo(ContainerState.RUNNING)

        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(CONTAINER_UNITS)
    }

    @Test
    @Order(12)
    fun `Deploy dapp`() {
        testLogger.info("Deploying dapp to c1")
        deployDapp("test_crosschain_transfer", containerName, assertSigners = arrayOf(node1, node2))
        dappBrid = dapps["test_crosschain_transfer"]!!
    }

    @Test
    @Order(13)
    fun `Perform cross-chain transfers between EC and dapp`() {
        testLogger.info("Initializing cross chain transfer dApp")
        val chromiaClientProvider = ChromiaClientProvider(
                ContainerClusterManagement(
                        ClusterManagementImpl(node1.c0),
                        mapOf(
                                systemCluster to listOf(node1.peerInfo(), node2.peerInfo()),
                                APP_CLUSTER1 to listOf(node1.peerInfo())
                        )
                ),
                PostchainClientConfig(BlockchainRid.ZERO_RID, EndpointPool.singleUrl(""), merkleHashVersion = 2)
        )
        val iccfProofTxMaterialBuilder = IccfProofTxMaterialBuilder(chromiaClientProvider)
        val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

        val aliceDappAuthenticator = registerAccount(node1, dappAdminKeyPair, dappBrid, aliceKeyPair, "Alice")

        node1.tx(dappBrid, "init", gtv(ecBrid), gtv(assetId))

        testLogger.info("Transfer tCHR to dApp from EC")
        val initialEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val initialDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)
        assertThat(initialDappAliceBalance).isNull()

        performCrossChainTransfer(node1, iccfProofTxMaterialBuilder, merkleHashCalculator, aliceAuthenticator, ecBrid, dappBrid, BigInteger.TEN, assetId)

        val afterTransferEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val afterTransferDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)!!.amount
        assertThat(initialEcAliceBalance - afterTransferEcAliceBalance).isEqualTo(BigInteger.TEN)
        assertThat(afterTransferDappAliceBalance).isEqualTo(BigInteger.TEN)

        testLogger.info("Transfer tCHR to EC from dApp")
        performCrossChainTransfer(node1, iccfProofTxMaterialBuilder, merkleHashCalculator, aliceDappAuthenticator, dappBrid, ecBrid, BigInteger.TEN, assetId)

        val afterTransferBackEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val afterTransferBackDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)
        assertThat(afterTransferBackEcAliceBalance).isEqualTo(initialEcAliceBalance)
        assertThat(afterTransferBackDappAliceBalance).isNull()
    }

    @Test
    @Order(14)
    fun `Register new dapp provider`() {
        val newDappProvider = cryptoSystem.generateKeyPair()

        aliceAuthenticator.verifyOperationAuthFlags("register_dapp_provider")
        aliceAuthenticator.transactionBuilder()
                .registerDappProviderOperation(containerName, newDappProvider.pubKey.data)
                .postTransactionUntilConfirmed("Registering new dApp provider ${newDappProvider.pubKey}")

        val containerVoterSet = node1.c0.getContainerData(containerName).deployer
        // Assert new provider is added
        awaitUntilAsserted {
            voteOnAllProposals(listOf(node1.provider))
            assertThat(node1.c0.getVoterSetMembers(containerVoterSet).map { PubKey(it) })
                    .containsOnly(node1.provider.pubKey, newDappProvider.pubKey)
        }
    }

    @Test
    @Order(15)
    fun `Upgrade container resources`() {
        // Test upgrades container by increasing resources within the same cluster.
        // Note: Cluster changes are no longer supported by upgradeContainerOperation.
        testLogger.info("Upgrade container resources")

        // Verify that attempting to change clusters fails
        aliceAuthenticator.verifyOperationAuthFlags("upgrade_container")
        val failedTx = aliceAuthenticator.transactionBuilder()
                .upgradeContainerOperation(
                        containerName, CONTAINER_UNITS + 1, EXTRA_STORAGE_GIB, "nonExistentCluster", 1, 0)
                .postTransactionUntilConfirmed("Attempt cluster change")
        assertThat(failedTx.status).isEqualTo(TransactionStatus.REJECTED)
        assertThat(failedTx.rejectReason ?: "").contains("Container migration is not supported")

        // Upgrading the container within the same cluster
        aliceAuthenticator.verifyOperationAuthFlags("upgrade_container")
        val tcRid = aliceAuthenticator.transactionBuilder()
                .upgradeContainerOperation(
                        containerName, CONTAINER_UNITS + 1, EXTRA_STORAGE_GIB, APP_CLUSTER1, 1, 0)
                .postTransactionUntilConfirmed("Upgrade Container")
                .txRid

        awaitUntilAsserted {
            val ticket = aliceAuthenticator.client.getUpgradeContainerTicketByTransaction(tcRid.rid.hexStringToByteArray())
            assertThat(ticket).isNotNull()
            assertThat(ticket!!.state).isEqualTo(TicketState.SUCCESS)
        }

        // Verify lease data
        val leaseDataList = aliceAuthenticator.client.getLeasesByAccount(aliceAuthenticator.accountId)
        assertThat(leaseDataList.size).isEqualTo(1)
        val leaseData = leaseDataList.first()
        assertThat(leaseData.containerName).isEqualTo(containerName)
        assertThat(leaseData.clusterName).isEqualTo(APP_CLUSTER1)
        assertThat(leaseData.containerUnits).isEqualTo(CONTAINER_UNITS + 1)

        // Verify container data
        val containerData = node1.c0.getContainerData(leaseData.containerName)
        assertThat(containerData).isNotNull()
        assertThat(containerData.cluster).isEqualTo(APP_CLUSTER1)
        assertThat(containerData.state).isEqualTo(ContainerState.RUNNING)
        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(CONTAINER_UNITS + 1L)

        // Verify blockchain remains RUNNING in the same cluster/container (no pause/migration needed)
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)
        val bcInfo = node2.c0.getBlockchainInfo(dappBrid.data)
        assertThat(bcInfo?.cluster).isEqualTo(APP_CLUSTER1)
        assertThat(bcInfo?.container).isEqualTo(containerName)

        // Verify new dapp blocks are being built
        val dappClient = node2.client(dappBrid)
        val height = dappClient.currentBlockHeight()
        awaitUntilAsserted {
            assertThat(dappClient.currentBlockHeight()).isGreaterThan(height + 2L)
        }

        // Verify new blocks continue to be anchored on the same CAC after upgrade
        awaitUntilAsserted {
            assertThat(
                    node2.client(CAC1).getLastAnchoredBlock(dappBrid)!!.blockHeight
            ).isGreaterThan(height + 2L)
        }
    }

    @Test
    @Order(16)
    fun `Link provider account to evm EOA account and update auth description signer with evm address`() {
        val metamaskPrivateKey = "8AA1F227F18B049D72C53B4565F2FC9D3D9A60DAAB938977CEFC3C9393D560D8"
        val evmKeyPair = ECKeyPair.create(BigInteger(metamaskPrivateKey, 16))
        val addressString = Keys.getAddress(evmKeyPair.publicKey)
        val addressByteArray = addressString.hexStringToByteArray()
        val accountId = gtv(provider1KeyPair.pubKey.data).merkleHash(hashCalculator)

        val accountMainAuthDescriptor = node1.client(ecBrid).getAccountMainAuthDescriptor(accountId)

        val updateMainAuthDescriptorSignature = getUpdateMainAuthDescriptorSignature(addressByteArray, accountMainAuthDescriptor, evmKeyPair)
        val linkEvmEoaAccountSignature = getLinkEvmEoaAccountSignature(addressByteArray, evmKeyPair, accountId, ecBrid)

        node1.client(ecBrid, listOf(provider1KeyPair)).transactionBuilder().addNop()
                .evmSignaturesOperation(listOf(addressByteArray), listOf(linkEvmEoaAccountSignature))
                .ftAuthOperation(accountId, accountMainAuthDescriptor.id.data)
                .linkEvmEoaAccountOperation(addressByteArray)
                .evmSignaturesOperation(listOf(addressByteArray), listOf(updateMainAuthDescriptorSignature))
                .ftAuthOperation(accountId, accountMainAuthDescriptor.id.data)
                .updateMainAuthDescriptorOperation(AuthDescriptor(AuthType.S, listOf(gtv(gtv("A"), gtv("T")), gtv(addressByteArray)), GtvNull))
                .postTransactionUntilConfirmed("Link EVM account and update auth description signer with evm address")

        val eoaAddressesForAccount = node1.client(ecBrid).getEoaAddressesForAccount(accountId)
        assertThat(eoaAddressesForAccount[0].toHex()).isEqualTo(addressString.uppercase())

        val newAccountMainAuthDescriptor = node1.client(ecBrid).getAccountMainAuthDescriptor(accountId)
        assertThat(newAccountMainAuthDescriptor.args[1].asByteArray().toHex()).isEqualTo(addressString.uppercase())
    }

    @Test
    @Order(20)
    fun `Add multi-key for node1 provider`() {
        val providerSecondKey = cryptoSystem.generateKeyPair()
        val providerThirdKey = cryptoSystem.generateKeyPair()
        val providerFourthKey = cryptoSystem.generateKeyPair()
        val keyNotUsed = cryptoSystem.generateKeyPair()

        // Only first key is valid
        verifyProviderAuth(listOf(listOf(node1.provider)), TransactionStatus.CONFIRMED)
        // The second key is not yet valid
        verifyProviderAuth(listOf(listOf(providerSecondKey)), TransactionStatus.REJECTED)

        // Add a second key to provider 1
        node1.client(chain0Brid, listOf(node1.provider, providerSecondKey)).transactionBuilder()
                .addProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.main, providerSecondKey.pubKey)
                .postTransactionUntilConfirmed("Add key to provider 1")

        // Both keys are valid since the default threshold is 1
        verifyProviderAuth(listOf(
                listOf(node1.provider),
                listOf(providerSecondKey),
                listOf(node1.provider, providerSecondKey)
        ), TransactionStatus.CONFIRMED)
        // But not other keys
        verifyProviderAuth(listOf(
                listOf(aliceKeyPair),
                listOf(keyNotUsed)
        ), TransactionStatus.REJECTED)

        // Verify keys returned by query
        var providerKeys = node1.c0.getProviderKeys(node1.provider.pubKey)
        assertThat(providerKeys.providerPubkey.data).isEqualTo(node1.provider.pubKey.data)
        assertThat(providerKeys.keys.size).isEqualTo(1)
        assertThat(providerKeys.keys[0].role).isEqualTo(ProviderKeyRole.main)
        assertThat(providerKeys.keys[0].threshold).isEqualTo(1)
        assertThat(providerKeys.keys[0].keys.map { it.toHex() })
                .isEqualTo(listOf(node1.provider.pubKey.hex(), providerSecondKey.pubKey.hex()))

        // Change the threshold to super-majority
        node1.client(chain0Brid, listOf(node1.provider, providerSecondKey)).transactionBuilder()
                .setProviderKeyThresholdOperation(node1.providerPubkey, ProviderKeyRole.main, 0)
                .postTransactionUntilConfirmed("Threshold set to super-majority")

        // Verify keys returned by query
        providerKeys = node1.c0.getProviderKeys(node1.provider.pubKey)
        assertThat(providerKeys.providerPubkey.data).isEqualTo(node1.provider.pubKey.data)
        assertThat(providerKeys.keys.size).isEqualTo(1)
        assertThat(providerKeys.keys[0].role).isEqualTo(ProviderKeyRole.main)
        assertThat(providerKeys.keys[0].threshold).isEqualTo(0)
        assertThat(providerKeys.keys[0].keys.map { it.toHex() })
                .isEqualTo(listOf(node1.provider.pubKey.hex(), providerSecondKey.pubKey.hex()))

        // Super-majority requires both keys
        verifyProviderAuth(listOf(
                listOf(node1.provider, providerSecondKey),
        ), TransactionStatus.CONFIRMED)
        verifyProviderAuth(listOf(
                listOf(node1.provider),
                listOf(providerSecondKey),
                listOf(keyNotUsed)
        ), TransactionStatus.REJECTED)

        // Add third and fourth key
        node1.client(chain0Brid, listOf(node1.provider, providerSecondKey, providerThirdKey, providerFourthKey)).transactionBuilder()
                .addProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.main, providerThirdKey.pubKey)
                .addProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.main, providerFourthKey.pubKey)
                .postTransactionUntilConfirmed("Add third and fourth key")

        // Super-majority requires 3 out of 4 keys
        verifyProviderAuth(listOf(
                listOf(node1.provider, providerSecondKey, providerThirdKey),
                listOf(node1.provider, providerSecondKey, providerFourthKey),
                listOf(providerSecondKey, providerThirdKey, providerFourthKey),
        ), TransactionStatus.CONFIRMED)
        verifyProviderAuth(listOf(
                listOf(node1.provider),
                listOf(providerSecondKey),
                listOf(providerThirdKey),
                listOf(providerFourthKey),
                listOf(keyNotUsed)
        ), TransactionStatus.REJECTED)

        // Add third key to a different role
        node1.client(chain0Brid, listOf(node1.provider, providerSecondKey, providerThirdKey)).transactionBuilder()
                .addProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.configuration_proposal_vote, providerThirdKey.pubKey)
                .postTransactionUntilConfirmed("Add third key to ${ProviderKeyRole.configuration_proposal_vote}")

        // No change here - still super-majority requires 3 out of 4 keys
        verifyProviderAuth(listOf(
                listOf(node1.provider, providerSecondKey, providerThirdKey),
                listOf(node1.provider, providerSecondKey, providerFourthKey),
                listOf(providerSecondKey, providerThirdKey, providerFourthKey),
        ), TransactionStatus.CONFIRMED)
        verifyProviderAuth(listOf(
                listOf(node1.provider),
                listOf(providerSecondKey),
                listOf(providerThirdKey),
                listOf(providerFourthKey),
                listOf(keyNotUsed)
        ), TransactionStatus.REJECTED)

        // Verify keys returned by query
        providerKeys = node1.c0.getProviderKeys(node1.provider.pubKey)
        assertThat(providerKeys.providerPubkey.data).isEqualTo(node1.provider.pubKey.data)
        assertThat(providerKeys.keys.size).isEqualTo(2)
        assertThat(providerKeys.keys[0].role).isEqualTo(ProviderKeyRole.main)
        assertThat(providerKeys.keys[0].threshold).isEqualTo(0)
        assertThat(providerKeys.keys[0].keys.map { it.toHex() })
                .isEqualTo(listOf(node1.provider.pubKey.hex(), providerSecondKey.pubKey.hex(), providerThirdKey.pubKey.hex(), providerFourthKey.pubKey.hex()))
        assertThat(providerKeys.keys[1].role).isEqualTo(ProviderKeyRole.configuration_proposal_vote)
        assertThat(providerKeys.keys[1].threshold).isEqualTo(1)
        assertThat(providerKeys.keys[1].keys.map { it.toHex() })
                .isEqualTo(listOf(providerThirdKey.pubKey.hex()))

        // Revoke the first key and set threshold to 2 - key removed key is signing the transaction before it is being removed
        node1.client(chain0Brid, listOf(node1.provider, providerSecondKey, providerThirdKey, providerFourthKey)).transactionBuilder()
                .revokeProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.main, node1.provider.pubKey)
                .setProviderKeyThresholdOperation(node1.providerPubkey, ProviderKeyRole.main, 2)
                .postTransactionUntilConfirmed("Revoke first provider key")

        // 2 keys will be enough
        verifyProviderAuth(listOf(
                listOf(providerSecondKey, providerThirdKey),
                listOf(providerThirdKey, providerFourthKey),
        ), TransactionStatus.CONFIRMED)
        verifyProviderAuth(listOf(
                listOf(node1.provider, providerSecondKey),
                listOf(keyNotUsed)
        ), TransactionStatus.REJECTED)

        // Reset keys for following tests - add node key back and set threshold to 1
        node1.client(chain0Brid, listOf(node1.provider)).transactionBuilder()
                .addProviderKeyOperation(node1.providerPubkey, ProviderKeyRole.main, node1.provider.pubKey)
                .setProviderKeyThresholdOperation(node1.providerPubkey, ProviderKeyRole.main, 1)
                .postTransactionUntilConfirmed("Reset keys")
    }

    fun verifyProviderAuth(signerListToVerify: List<List<KeyPair>>, expectedTxStatus: TransactionStatus) {
        signerListToVerify.forEach {
            val transactionBuilder = node1.client(chain0Brid, it).transactionBuilder()
                    .addNop()
                    .updateProviderOperation(node1.provider.pubKey.data, "new-name", "new-url")
            val response = if (expectedTxStatus == TransactionStatus.REJECTED)
                transactionBuilder.post()
            else
                transactionBuilder.postTransactionUntilConfirmed("Testing updating provider info signed by $it and expecting tx status $expectedTxStatus")
            assertThat(response.status).isEqualTo(expectedTxStatus)
            if (expectedTxStatus == TransactionStatus.REJECTED) {
                assertThat(response.rejectReason).isNotNull().contains("Operation must be signed by provider key(s)")
            }
        }
    }

    private fun getUpdateMainAuthDescriptorSignature(addressByteArray: ByteArray, accountMainAuthDescriptor: Ft4GetAccountMainAuthDescriptorResult, keyPair: ECKeyPair): Signature {
        val authDescriptorArgs = gtv(gtv(gtv("A"), gtv("T")), gtv(addressByteArray))
        val authDescriptor = gtv(
                gtv(AuthType.S.ordinal.toLong()),
                authDescriptorArgs,
                GtvNull)
        val updateMainAuthDescriptorOpArgs = listOf(authDescriptor)
        val authMessageTemplate = node1.client(ecBrid).getAuthMessageTemplate(UPDATE_MAIN_AUTH_DESCRIPTOR, gtv(updateMainAuthDescriptorOpArgs))

        val ecRid = node1.c0.getEconomyChainRid()
        val nonce = gtv(
                gtv(ecRid!!),
                gtv(UPDATE_MAIN_AUTH_DESCRIPTOR),
                gtv(updateMainAuthDescriptorOpArgs),
                gtv(0),
        ).merkleHash(hashCalculator)
        val authDescriptorAccountId = accountMainAuthDescriptor.accountId

        val authMessage = authMessageTemplate
                .replace("{blockchain_rid}", ecRid.toHex())
                .replace("{nonce}", nonce.toHex().uppercase())
                .replace("{account_id}", authDescriptorAccountId.toHex().uppercase())

        val signatureData = Sign.signPrefixedMessage(
                authMessage.toByteArray(StandardCharsets.UTF_8),
                keyPair
        )
        val signature = Signature(
                signatureData.r.wrap(),
                signatureData.s.wrap(),
                BigInteger(signatureData.v).longValueExact()
        )
        return signature
    }
}

/**
 * Helpers copied from postchain-eif
 */

fun EventMerkleProof.web3EventData() = DynamicBytes(eventData)

fun EventMerkleProof.chromiaWeb3EventProof() = ChromiaTokenBridge.Proof(
        Bytes32(this.eventProof!!.leaf),
        // Don't delete !! to help the compiler with a smart cast, otherwise you will get
        // `Smart cast to '...' is impossible, because '...' is a public API property declared in different module
        Uint256(this.eventProof!!.position),
        DynamicArray(Bytes32::class.java, this.eventProof!!.merkleProofs.map { Bytes32(it) })
)

fun EventMerkleProof.web3BlockHeader() = DynamicBytes(blockHeader)

fun EventMerkleProof.web3Signatures() = DynamicArray(DynamicBytes::class.java, blockWitness!!.map { DynamicBytes(it.sig) })

fun EventMerkleProof.web3Signers() = DynamicArray(Address::class.java, blockWitness!!.map { Address(it.pubkey.toHex()) })

fun EventMerkleProof.chromiaWeb3ExtraProofData() = ChromiaTokenBridge.ExtraProofData(
        DynamicBytes(extraMerkleProof!!.leaf),
        Bytes32(extraMerkleProof!!.hashedLeaf),
        Uint256(extraMerkleProof!!.position),
        Bytes32(extraMerkleProof!!.extraRoot),
        DynamicArray(Bytes32::class.java, extraMerkleProof!!.extraMerkleProofs.map { Bytes32(it) })
)
