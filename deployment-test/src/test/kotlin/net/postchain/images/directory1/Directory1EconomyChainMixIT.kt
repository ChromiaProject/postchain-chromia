package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import mu.KotlinLogging
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchainInfo
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainerData
import net.postchain.chain0.common.queries.getNodeData
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
import net.postchain.chain0.economy_chain_test_auth_server.registerAccountOperation
import net.postchain.chain0.economy_chain_test_claim_tchr.claimTestChrOperation
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.lib.ft4.core.accounts.AuthDescriptor
import net.postchain.chain0.lib.ft4.core.accounts.AuthType
import net.postchain.chain0.lib.ft4.external.accounts.Ft4GetAccountMainAuthDescriptorResult
import net.postchain.chain0.lib.ft4.external.accounts.getAccountMainAuthDescriptor
import net.postchain.chain0.lib.ft4.external.accounts.updateMainAuthDescriptorOperation
import net.postchain.chain0.lib.ft4.external.crosschain.initTransferOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ContainerState
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.contracts.TestToken
import net.postchain.eif.contracts.TokenBridge
import net.postchain.eif.contracts.Validator
import net.postchain.eif.hbridge.getEoaAddressesForAccount
import net.postchain.eif.hbridge.linkEvmEoaAccountOperation
import net.postchain.eif.lib.ft4.core.auth.Signature
import net.postchain.eif.lib.ft4.external.assets.getAssetBalance
import net.postchain.eif.lib.ft4.external.assets.getAssetsByName
import net.postchain.eif.lib.ft4.external.auth.evmSignaturesOperation
import net.postchain.eif.lib.ft4.external.auth.ftAuthOperation
import net.postchain.eif.lib.ft4.external.auth.getAuthMessageTemplate
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.math.BigInteger
import java.nio.charset.StandardCharsets

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1EconomyChainMixIT {

    private val ecAdminKeyPair = KeyPair.of(
            "02552192E2FA6F1C1229EB74FBDC9F27EEB87641BA11B29F9094D4F729C081AFA3",
            "E9CF8BC054D6F853FA9457D95EDBCA76EF52CEAD2913674031513FB015F5B5C0")
    private val PostchainContainer.ecAdmin get() = client(ecBrid, listOf(ecAdminKeyPair))

    companion object : ManagedModeBase() {

        const val EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER = "EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER"
        const val EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER = "EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER"
        const val EIF_EC_STRATEGY_SENDER_BLOCKCHAIN = "EIF_EC_STRATEGY_SENDER_BLOCKCHAIN"
        const val EVM_EVENT_RECEIVER_CHAIN_NAME = "evm_event_receiver_chain"
        const val EC_CHAIN_NAME = "economy_chain"

        const val ETH_ASSET_ADDRESS = "Aa1ae68ABcd32804132370B9f73c3160dbbfC593"
        const val ETH_ASSET_NETWORK_ID = "11155111"
        const val ASSET_NAME = "tCHR"

        const val APP_CLUSTER1 = "appCluster1"
        const val APP_CLUSTER2 = "appCluster2"
        const val APP_CLUSTER_TAG = "appClusterTag"
        const val CONTAINER_UNITS = 2L
        const val DURATION_WEEKS = 1L
        const val EXTRA_STORAGE_GIB = 0L
        const val SCU_PRICE = 1L
        const val EXTRA_STORAGE_PRICE = 1L
        const val PROVIDER1_VS = "provider1_vs"
        const val PROVIDER2_VS = "provider2_vs"

        private val evmContainerLogger = KotlinLogging.logger("EC_EvmContainerLogger")
        private val node1Logger = KotlinLogging.logger("EC_Node1Logger")
        private val node2Logger = KotlinLogging.logger("EC_Node2Logger")
        private val node3Logger = KotlinLogging.logger("EC_Node3Logger")
        override val logsSubdir = "ec"

        val hashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

        private val node1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        lateinit var containerName: String
        lateinit var dappBrid: BlockchainRid
        lateinit var CAC1: BlockchainRid
        lateinit var CAC2: BlockchainRid

        // EIF
        private val evmContainer: GethContainer
        private val evmContainerCredentials = Credentials.create("0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")
        private val evmContainerNetworkId = 1337L
        private val web3j: Web3j
        private val transactionManager: TransactionManager
        private val gasProvider = DefaultGasProvider()
        private lateinit var validator: Validator
        private lateinit var bridge: TokenBridge
        private lateinit var bridgeAddress: String
        private lateinit var testToken: TestToken
        private lateinit var testTokenAddress: String
        private val node0EvmAddress = Address("659e4a3726275edFD125F52338ECe0d54d15BD99")
        private lateinit var eventReceiverBrid: BlockchainRid

        // EIF / ABI
        private val validatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/Validator.sol/Validator.json")
        private val tokenBridgeBinary = getBinaryFromArtifactResource("/artifacts/contracts/TokenBridge.sol/TokenBridge.json")
        private val testTokenBinary = getBinaryFromArtifactResource("/artifacts/contracts/token/TestToken.sol/TestToken.json")

        // EIF / balances
        private val INITIAL_SUPPLY = BigInteger.valueOf(1_000_000_000L)
        private const val DEPOSIT_NUMBER = 5
        private val depositAmount = BigInteger.valueOf(1000)
        private val totalDepositedAmount = DEPOSIT_NUMBER.toBigInteger() * depositAmount
        private lateinit var assetId: ByteArray

        // EIF / users
        // EIF / users / Alice
        private val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
        private val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
        private val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)
        private val aliceEvmAddressStr = "e105ba42b66d08ac7ca7fc48c583599044a6dab3"
        private val aliceEvmAddress = aliceEvmAddressStr.hexStringToByteArray()
        private lateinit var aliceAuthenticator: FTAuthenticator

        // EIF / users / Bob
        private val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
        private val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
        private val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)
        private val bobEvmAddressStr = "661683e5d36E83B38B1a20247ba6F5c410dC165d"
        private val bobEvmAddress = bobEvmAddressStr.hexStringToByteArray()
        private lateinit var bobAccountId: ByteArray

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
                    evmContainerCredentials,
                    PollingTransactionReceiptProcessor(web3j, 1000, 30)
            )

            // Nodes
            chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    node1KeyPair,
                    "config-mix"
            ).withEifEnv()

            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix"
            ).withEifEnv()

            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-mix"
            )
                    .withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                    .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                    .withMasterDockerConfig()
                    .withClasspathResourceMapping(
                            "${this::class.java.getResource("config-mix")!!.path.substringAfter("test-classes/")}/node3",
                            PostchainContainer.MOUNT_DIR, BindMode.READ_ONLY
                    )
                    .withEnv("POSTCHAIN_CONFIG", "${PostchainContainer.MOUNT_DIR}/node-config.properties")
                    .withEnv("POSTCHAIN_SUBNODE_LOG4J_CONFIGURATION_FILE", this::class.java.getResource("/log/log4j2.yml")!!.path)
                    .withEifEnv()

            removeSubnodeContainers()
            startNodesAndChain0()
        }

        private fun PostchainContainer.withEifEnv(): PostchainContainer {
            withEnv("POSTCHAIN_EIF_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
            withEnv("POSTCHAIN_EIF_ETHEREUM_MAX_READ_AHEAD", 200.toString())
            withEnv("POSTCHAIN_EIF_ETHEREUM_MAX_QUEUE_SIZE", 100.toString())
            withEnv("POSTCHAIN_EIF_EVM_MAX_TRY_ERRORS", 1.toString())
            return this
        }

        private fun getBinaryFromArtifactResource(resourcePath: String): String {
            val artifactFile = Directory1EconomyChainMixIT::class.java.getResource(resourcePath)?.readText()
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
                .createVoterSetOperation(node1.providerPubkey, "provider1_vs", 0, listOf(node1.providerPubkey), null)
                .createVoterSetOperation(node2.providerPubkey, "provider2_vs", 0, listOf(node2.providerPubkey), null)
                .postTransactionUntilConfirmed("Voter sets provider1_vs and provider2_vs created")
        awaitQueryResult {
            assertThat(node1.c0.getVoterSets().map { it.name }).containsAll(PROVIDER1_VS, PROVIDER2_VS)
        }
    }

    @Test
    @Order(2)
    fun `Deploy contracts on EVM`() {
        testLogger.info { "Deploying contracts on EVM" }

        // Deploy validator contract
        val encodedConstructor = FunctionEncoder.encodeConstructor(listOf(DynamicArray(Address::class.java, node0EvmAddress)))
        validator = Contract.deployRemoteCall(Validator::class.java, web3j, transactionManager, gasProvider, validatorBinary, encodedConstructor).send()

        // Deploy token bridge contract
        bridge = Contract.deployRemoteCall(TokenBridge::class.java, web3j, transactionManager, gasProvider, tokenBridgeBinary, "").send().apply {
            initialize(Address(validator.contractAddress), Uint256(2)).send()
        }
        bridgeAddress = bridge.contractAddress

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        testToken = Contract.deployRemoteCall(TestToken::class.java, web3j, transactionManager, gasProvider, testTokenBinary, "").send().apply {
            mint(Address(transactionManager.fromAddress), Uint256(INITIAL_SUPPLY)).send()
            approve(Address(bridge.contractAddress), Uint256(INITIAL_SUPPLY)).send()
        }
        testTokenAddress = testToken.contractAddress

        // Allow token
        bridge.allowToken(Address(testToken.contractAddress)).send()

        // Assert initial balance
        val balance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(INITIAL_SUPPLY, balance.value)
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
                gtv(bridgeAddress.replace("^0x".toRegex(), ""))
        ).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))

        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!
                .readText()
                // inject Event Receiver RID
                .replace(
                        "<string>$EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER</string>",
                        "<bytea>${eventReceiverBrid.toHex()}</bytea>"
                )
                .replace(
                        "<string>$EIF_EC_STRATEGY_SENDER_BLOCKCHAIN</string>",
                        "<string>\"${senderVirtualBrid.toHex()}\"</string>"
                )
                // use `TST TestToken ERC-20` instead of `CHR Chromia ERC-20`
                .replace(ETH_ASSET_ADDRESS, testTokenAddress.substring(2))
                .replace(ETH_ASSET_NETWORK_ID, evmContainerNetworkId.toString())
        )

        node1.c0.transactionBuilder()
                .initEconomyChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(economyChainGtvConfig))
                .postTransactionUntilConfirmed("Add $EC_CHAIN_NAME")

        val ecRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EC_CHAIN_NAME }?.rid
        assertThat(ecRid).isNotNull()
        ecBrid = BlockchainRid(ecRid!!)

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
        aliceAuthenticator = registerAccount(ecBrid, aliceKeyPair, "Alice")
        linkAccount(aliceAuthenticator, aliceEvmAddress)

        // Claim initial supply
        aliceAuthenticator.verifyOperationAuthFlags("claim_test_chr")
        aliceAuthenticator.transactionBuilder()
                .claimTestChrOperation()
                .postTransactionUntilConfirmed("Claiming initial supply")

        val aliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        testLogger.info("Alice account balance is: $aliceBalance")
        assertThat(aliceBalance).isEqualTo(INITIAL_SUPPLY)
    }

    @Test
    @Order(6)
    fun `Deposit token on EVM`() {
        testLogger.info { "Deposit token on EVM" }

        // deposit on EVM
        repeat(DEPOSIT_NUMBER) {
            bridge.deposit(Address(testToken.contractAddress), Uint256(depositAmount)).send()
        }
        // check the balance on EVM
        val aliceBalance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, INITIAL_SUPPLY - totalDepositedAmount)

        // check the asset balance on Chromia
        awaitQueryResult {
            val balance = node1.client(ecBrid).getAssetBalance(aliceAuthenticator.accountId, assetId)
            assertThat(balance?.amount).isEqualTo(INITIAL_SUPPLY + totalDepositedAmount)
        }
    }

    @Test
    @Order(7)
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
    @Order(8)
    fun `Add new tag`() {
        testLogger.info("Adding tag")
        with(node1.ec) {
            transactionBuilder()
                    .createTagOperation(APP_CLUSTER_TAG, SCU_PRICE, EXTRA_STORAGE_PRICE)
                    .postTransactionUntilConfirmed("$APP_CLUSTER_TAG tag created")

            makeVoteOnLatestProposal(node2)

            assertThat(getTagByName(APP_CLUSTER_TAG))
                    .isEqualTo(TagData(APP_CLUSTER_TAG, SCU_PRICE, EXTRA_STORAGE_PRICE))
        }
    }

    @Test
    @Order(9)
    fun `Add clusters`() {
        testLogger.info("Adding clusters")

        with(node1.ec) {
            transactionBuilder()
                    .createClusterOperation(APP_CLUSTER1, "SYSTEM_P", PROVIDER1_VS, CONTAINER_UNITS, EXTRA_STORAGE_GIB, APP_CLUSTER_TAG)
                    .createClusterOperation(APP_CLUSTER2, "SYSTEM_P", PROVIDER2_VS, CONTAINER_UNITS, EXTRA_STORAGE_GIB, APP_CLUSTER_TAG)
                    .postTransactionUntilConfirmed("$APP_CLUSTER1, $APP_CLUSTER2 clusters created")

            // Approve both APP_CLUSTER1 and APP_CLUSTER2
            makeVoteOnLatestProposal(node2)
            makeVoteOnLatestProposal(node2)

            awaitQueryResult {
                assertThat(getClusterCreationStatus(APP_CLUSTER1))
                        .isEqualTo(ClusterCreationStatus.SUCCESS)
            }
            awaitQueryResult {
                assertThat(getClusters().first { it.name == APP_CLUSTER1 })
                        .isNotNull()
            }
        }

        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder().addNop()
                // APP_CLUSTER1 / node1
//                .createClusterOperation(node1.providerPubkey, APP_CLUSTER1, "SYSTEM_P", listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, APP_CLUSTER1)
                // APP_CLUSTER2 / node2
//                .createClusterOperation(node2.providerPubkey, APP_CLUSTER2, "SYSTEM_P", listOf(node2.providerPubkey))
                .updateNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, APP_CLUSTER2)
                .postTransactionUntilConfirmed("$APP_CLUSTER1, $APP_CLUSTER2 clusters created")
        CAC1 = BlockchainRid(node1.c0.cmGetClusterInfo(APP_CLUSTER1).anchoringChain)
        CAC2 = BlockchainRid(node1.c0.cmGetClusterInfo(APP_CLUSTER2).anchoringChain)
    }

    @Test
    @Order(10)
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
        assertThat(containerData.proposedByPubkey).isEqualTo(node1KeyPair.pubKey.wData)
        assertThat(containerData.state).isEqualTo(ContainerState.RUNNING)

        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(CONTAINER_UNITS)
    }

    @Test
    @Order(11)
    fun `Deploy dapp`() {
        testLogger.info("Deploying dapp to c1")
        deployDapp("test_cross_chain_transfer", containerName, assertSigners = arrayOf(node1))
        dappBrid = dapps["test_cross_chain_transfer"]!!
    }

    @Test
    @Order(12)
    fun `Perform cross-chain transfers between EC and dapp`() {
        testLogger.info("Initializing cross chain transfer dApp")
        val chromiaClientProvider = ChromiaClientProvider(ContainerClusterManagement(
                ClusterManagementImpl(node1.c0),
                mapOf(
                        systemCluster to listOf(node1.peerInfo(), node2.peerInfo()),
                        APP_CLUSTER1 to listOf(node1.peerInfo())
                )
        ))
        val iccfProofTxMaterialBuilder = IccfProofTxMaterialBuilder(chromiaClientProvider)
        val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

        val aliceDappAuthenticator = registerAccount(dappBrid, aliceKeyPair, "Alice")

        node1.tx(dappBrid, "init", gtv(ecBrid), gtv(assetId))

        testLogger.info("Transfer tCHR to dApp from EC")
        val initialEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val initialDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)
        assertThat(initialDappAliceBalance).isNull()

        performCrossChainTransfer(iccfProofTxMaterialBuilder, merkleHashCalculator, aliceAuthenticator, ecBrid, dappBrid)

        val afterTransferEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val afterTransferDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)!!.amount
        assertThat(initialEcAliceBalance - afterTransferEcAliceBalance).isEqualTo(BigInteger.TEN)
        assertThat(afterTransferDappAliceBalance).isEqualTo(BigInteger.TEN)

        testLogger.info("Transfer tCHR to EC from dApp")
        performCrossChainTransfer(iccfProofTxMaterialBuilder, merkleHashCalculator, aliceDappAuthenticator, dappBrid, ecBrid)

        val afterTransferBackEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        val afterTransferBackDappAliceBalance = node1.client(dappBrid, listOf(aliceKeyPair)).getAssetBalance(aliceDappAuthenticator.accountId, assetId)
        assertThat(afterTransferBackEcAliceBalance).isEqualTo(initialEcAliceBalance)
        assertThat(afterTransferBackDappAliceBalance).isNull()
    }

    @Test
    @Order(13)
    fun `Register new dapp provider`() {
        val newDappProvider = cryptoSystem.generateKeyPair()

        aliceAuthenticator.verifyOperationAuthFlags("register_dapp_provider")
        aliceAuthenticator.transactionBuilder()
                .registerDappProviderOperation(containerName, newDappProvider.pubKey.data)
                .postTransactionUntilConfirmed("Registering new dApp provider ${newDappProvider.pubKey}")

        val containerVoterSet = node1.c0.getContainerData(containerName).deployer
        // Assert new provider is added
        awaitUntilAsserted {
            assertThat(node1.c0.getVoterSetMembers(containerVoterSet).map { PubKey(it) })
                    .containsOnly(node1.provider.pubKey, newDappProvider.pubKey)
        }
    }

    @Test
    @Order(14)
    fun `Upgrade container`() {
        testLogger.info("Upgrade container")

        // Container upgrade will cause container migration (all blockchains will be moved to a new container),
        // so we need to pause all blockchains.
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("test_dapp paused")
        // Verify blockchain is PAUSED and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)
        val lastHeightBeforeMoving = node1.client(dappBrid).currentBlockHeight()

        // Upgrading the container in a way that all blockchains to be moved to APP_CLUSTER_2
        aliceAuthenticator.verifyOperationAuthFlags("upgrade_container")
        val tcRid = aliceAuthenticator.transactionBuilder()
                .upgradeContainerOperation(
                        containerName, CONTAINER_UNITS + 1, EXTRA_STORAGE_GIB, APP_CLUSTER2)
                .postTransactionUntilConfirmed("Upgrade Container")
                .txRid

        awaitUntilAsserted {
            val ticket = aliceAuthenticator.client.getUpgradeContainerTicketByTransaction(tcRid.rid.hexStringToByteArray())
            assertThat(ticket).isNotNull()
            assertThat(ticket!!.state).isEqualTo(TicketState.SUCCESS)
        }

        val leaseDataList = aliceAuthenticator.client.getLeasesByAccount(aliceAuthenticator.accountId)
        assertThat(leaseDataList.size).isEqualTo(1)
        val leaseData = leaseDataList[0]
        assertThat(leaseData.clusterName).isEqualTo(APP_CLUSTER2)
        assertThat(leaseData.containerUnits).isEqualTo(CONTAINER_UNITS + 1)

        val newContainerName = containerName + "_new" // TODO: Improve new name
        assertThat(leaseData.containerName).isEqualTo(newContainerName)
        val containerData = node1.c0.getContainerData(leaseData.containerName)
        assertThat(containerData).isNotNull()
        assertThat(containerData.cluster).isEqualTo(APP_CLUSTER2)
        assertThat(containerData.proposedByPubkey).isEqualTo(node1KeyPair.pubKey.wData)
        assertThat(containerData.state).isEqualTo(ContainerState.RUNNING)

        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(3)

        // Resuming blockchain
        node1.c0.transactionBuilder().addNop()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "")
                .postTransactionUntilConfirmed("test_dapp resumed")

        // Verify blockchain is RUNNING and all blocks are anchored
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)
        val bcInfo = node2.c0.getBlockchainInfo(dappBrid.data)
        assertThat(bcInfo?.cluster).isEqualTo(APP_CLUSTER2)
        assertThat(bcInfo?.container).isEqualTo(newContainerName)

        // Asserting that all blocks (some of them) are re-anchored on appCluster2's CAC chain
        assertBlockReanchored(dappBrid, node1, CAC1, node2, CAC2, 0)
        assertBlockReanchored(dappBrid, node1, CAC1, node2, CAC2)

        // Making sure a few new blocks of dapp are built
        val dappClient = node2.client(dappBrid)
        val height = dappClient.currentBlockHeight()
        awaitUntilAsserted {
            assertThat(dappClient.currentBlockHeight()).isGreaterThan(height + 2)
        }

        // Asserting that new blocks are anchored on s2CAC chain
        awaitUntilAsserted {
            val lastAnchoredHeight2 = node2.client(CAC2).getLastAnchoredBlock(dappBrid)!!.blockHeight
            assertThat(lastAnchoredHeight2).isGreaterThan(lastHeightBeforeMoving)
        }
    }

    @Test
    @Order(15)
    fun `Link provider account to evm EOA account and update auth description signer with evm address`() {
        val metamaskPrivateKey = "8AA1F227F18B049D72C53B4565F2FC9D3D9A60DAAB938977CEFC3C9393D560D8"
        val evmKeyPair = ECKeyPair.create(BigInteger(metamaskPrivateKey, 16))
        val addressString = Keys.getAddress(evmKeyPair.publicKey)
        val addressByteArray = addressString.hexStringToByteArray()
        val accountId = gtv(node1KeyPair.pubKey.data).merkleHash(hashCalculator)

        val accountMainAuthDescriptor = node1.client(ecBrid).getAccountMainAuthDescriptor(accountId)

        val updateMainAuthDescriptorSignature = getUpdateMainAuthDescriptorSignature(addressByteArray, accountMainAuthDescriptor, evmKeyPair)
        val linkEvmEoaAccountSignature = getLinkEvmEoaAccountSignature(addressByteArray, evmKeyPair)

        node1.client(ecBrid, listOf(node1KeyPair)).transactionBuilder().addNop()
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

    private fun registerAccount(blockchainRid: BlockchainRid, userKeyPair: KeyPair, username: String): FTAuthenticator {
        node1.client(blockchainRid, listOf(ecAdminKeyPair)).transactionBuilder().addNop()
                .registerAccountOperation(userKeyPair.pubKey)
                .postTransactionUntilConfirmed("Register $username account")

        return FTAuthenticator(userKeyPair, node1.client(blockchainRid, listOf(userKeyPair)))
    }

    private fun getLinkEvmEoaAccountSignature(addressByteArray: ByteArray, evmKeyPair: ECKeyPair): Signature {
        val opName = "eif.hbridge.link_evm_eoa_account"
        val opArgs = gtv(listOf(gtv(addressByteArray)))

        val nonce = gtv(listOf(
                gtv(ecBrid.data),
                gtv(opName),
                opArgs,
                gtv(0),
        )).merkleHash(hashCalculator)

        val template = node1.client(ecBrid).getAuthMessageTemplate(opName, opArgs)
        val message = template.replace("{blockchain_rid}", ecBrid.toHex().uppercase())
                .replace("{nonce}", nonce.toHex().uppercase())

        val evmSig = Sign.signPrefixedMessage(
                message.toByteArray(StandardCharsets.UTF_8),
                evmKeyPair
        )
        val signature = Signature(
                evmSig.r.wrap(),
                evmSig.s.wrap(),
                BigInteger(evmSig.v).longValueExact()
        )
        return signature
    }

    private fun linkAccount(userAuthenticator: FTAuthenticator, userEvmAddress: ByteArray) {
        val signature = getLinkEvmEoaAccountSignature(userEvmAddress, evmContainerCredentials.ecKeyPair)

        userAuthenticator.client.transactionBuilder().addNop()
                .evmSignaturesOperation(listOf(userEvmAddress), listOf(signature))
                .ftAuthOperation(aliceAuthenticator.accountId, aliceAuthenticator.authDescriptor.id.data)
                .linkEvmEoaAccountOperation(userEvmAddress)
                .postTransactionUntilConfirmed("Link EVM account")
    }

    private fun getUpdateMainAuthDescriptorSignature(addressByteArray: ByteArray, accountMainAuthDescriptor: Ft4GetAccountMainAuthDescriptorResult, keyPair: ECKeyPair): Signature {
        val updateMainAuthDescriptorOpName = "ft4.update_main_auth_descriptor"
        val authDescriptorArgs = gtv(gtv(gtv("A"), gtv("T")), gtv(addressByteArray))
        val authDescriptor = gtv(
                gtv(AuthType.S.ordinal.toLong()),
                authDescriptorArgs,
                GtvNull)
        val updateMainAuthDescriptorOpArgs = listOf(authDescriptor)
        val authMessageTemplate = node1.client(ecBrid).getAuthMessageTemplate(updateMainAuthDescriptorOpName, gtv(updateMainAuthDescriptorOpArgs))

        val ecRid = node1.c0.getEconomyChainRid()
        val nonce = gtv(
                gtv(ecRid!!),
                gtv(updateMainAuthDescriptorOpName),
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

    private fun performCrossChainTransfer(
            iccfProofTxMaterialBuilder: IccfProofTxMaterialBuilder,
            hashCalculator: GtvMerkleHashCalculator,
            sourceAccountAuthenticator: FTAuthenticator,
            sourceChain: BlockchainRid,
            destinationChain: BlockchainRid
    ) {
        val initTransferTxRid = sourceAccountAuthenticator.transactionBuilder()
                .initTransferOperation(sourceAccountAuthenticator.accountId, assetId, BigInteger.TEN, listOf(destinationChain.data), Long.MAX_VALUE)
                .postAwaitConfirmation().txRid

        val initTransferTx = GtvDecoder.decodeGtv(node1.client(sourceChain).getTransaction(initTransferTxRid))

        val initTxProof = awaitQueryResult {
            iccfProofTxMaterialBuilder.build(
                    initTransferTxRid,
                    initTransferTx.merkleHash(hashCalculator),
                    listOf(aliceKeyPair.pubKey),
                    sourceChain,
                    destinationChain,
                    forceIntraNetworkIccfOperation = true
            )
        }!!

        val applyTransferTxRid = initTxProof.txBuilder
                .addOperation("ft4.crosschain.apply_transfer", initTransferTx, gtv(1), initTransferTx, gtv(1), gtv(0))
                .postAwaitConfirmation().txRid

        val applyTransferTx = GtvDecoder.decodeGtv(node1.client(destinationChain).getTransaction(applyTransferTxRid))

        val applyTxProof = awaitQueryResult {
            iccfProofTxMaterialBuilder.build(
                    applyTransferTxRid,
                    applyTransferTx.merkleHash(hashCalculator),
                    listOf(),
                    destinationChain,
                    sourceChain,
                    forceIntraNetworkIccfOperation = true
            )
        }!!

        applyTxProof.txBuilder
                .addOperation("ft4.crosschain.complete_transfer", applyTransferTx, gtv(1))
                .postAwaitConfirmation()

    }
}