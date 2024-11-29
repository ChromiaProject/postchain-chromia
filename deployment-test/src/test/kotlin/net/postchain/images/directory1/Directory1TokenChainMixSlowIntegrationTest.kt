package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import mu.KotlinLogging
import net.postchain.chain0.GtxTransaction
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.common_proposal.getCommonProposalsRange
import net.postchain.chain0.common_proposal.makeCommonVoteOperation
import net.postchain.chain0.direct_container.createContainerWithUnitsOperation
import net.postchain.chain0.economy_chain.getBalance
import net.postchain.chain0.economy_chain.initOperation
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.economy_chain_test_auth_server.registerAccountOperation
import net.postchain.chain0.economy_chain_test_claim_tchr.faucetOperation
import net.postchain.chain0.lib.ft4.core.accounts.AuthDescriptor
import net.postchain.chain0.lib.ft4.core.accounts.AuthType
import net.postchain.chain0.lib.ft4.core.accounts.strategies.transfer.fee.rasTransferFeeOperation
import net.postchain.chain0.lib.ft4.external.accounts.strategies.registerAccountOperation
import net.postchain.chain0.lib.ft4.external.crosschain.APPLY_TRANSFER
import net.postchain.chain0.lib.ft4.external.crosschain.COMPLETE_TRANSFER
import net.postchain.chain0.lib.ft4.external.crosschain.initTransferOperation
import net.postchain.chain0.lib.hbridge.BridgeMode
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_container.proposeContainerOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.chain0.token_chain.BridgeConfiguration
import net.postchain.chain0.token_chain.MintingPolicy
import net.postchain.chain0.token_chain.initTokenChainOperation
import net.postchain.chain0.token_chain.mintTokenOperation
import net.postchain.chain0.token_chain.proposeTokenBridgeOperation
import net.postchain.chain0.token_chain.proposeTokenOperation
import net.postchain.chain0.token_chain.rasIccfOperation
import net.postchain.chain0.token_chain_in_directory_chain.initEvmEventReceiverTokenChainOperation
import net.postchain.chain0.token_chain_in_directory_chain.initTokenChainOperation
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.contracts.TestToken
import net.postchain.eif.contracts.TokenBridge
import net.postchain.eif.contracts.Validator
import net.postchain.eif.hbridge.LINK_EVM_EOA_ACCOUNT
import net.postchain.eif.hbridge.getBridgeContracts
import net.postchain.eif.hbridge.linkEvmEoaAccountOperation
import net.postchain.eif.lib.ft4.core.auth.Signature
import net.postchain.eif.lib.ft4.external.accounts.getAccountById
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
import net.postchain.gtv.mapper.GtvObjectMapper
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
class Directory1TokenChainMixSlowIntegrationTest {

    companion object : ManagedModeBase() {

        private val ecAdminKeyPair = KeyPair.of(
                "02552192E2FA6F1C1229EB74FBDC9F27EEB87641BA11B29F9094D4F729C081AFA3",
                "E9CF8BC054D6F853FA9457D95EDBCA76EF52CEAD2913674031513FB015F5B5C0")

        const val EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER = "EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER"
        const val EIF_TC_EVENT_RECEIVER_BRID_PLACEHOLDER = "EIF_TC_EVENT_RECEIVER_BRID_PLACEHOLDER"
        const val EVM_EVENT_RECEIVER_CHAIN_NAME = "evm_event_receiver_token_chain"
        const val EC_CHAIN_NAME = "economy_chain"
        const val TC_CHAIN_NAME = "token_chain"

        const val ASSET_NAME = "tCHR"

        private val evmContainerLogger = KotlinLogging.logger("TC_EvmContainerLogger")
        private val node1Logger = KotlinLogging.logger("TC_Node1Logger")
        private val node2Logger = KotlinLogging.logger("TC_Node2Logger")
        private val node3Logger = KotlinLogging.logger("TC_Node3Logger")
        override val logsSubdir = "tc"

        val hashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

        private val node1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

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
        private val depositAmount = BigInteger.valueOf(1000)
        private lateinit var chrAssetId: ByteArray

        // EIF / users
        // EIF / users / Alice
        private val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
        private val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
        private val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)
        private val aliceEvmAddressStr = "e105ba42b66d08ac7ca7fc48c583599044a6dab3"
        private val aliceEvmAddress = aliceEvmAddressStr.hexStringToByteArray()
        private lateinit var aliceAuthenticator: FTAuthenticator
        private lateinit var aliceTcAuthenticator: FTAuthenticator

        // EIF / users / Bob
        private val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
        private val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
        private val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)

        private lateinit var tcBrid: BlockchainRid
        private val PostchainContainer.tc get() = client(tcBrid)
        private lateinit var testTokenAssetId: ByteArray

        private lateinit var accountCreationChainBrid: BlockchainRid

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
            )
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
                    .withEifEnv()

            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-mix"
            )
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
            val artifactFile = Directory1EconomyChainMixSlowIntegrationTest::class.java.getResource(resourcePath)?.readText()
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
        getDb(node1).awaitBlockHeight(0)
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
    fun `Deploy Economy Chain`() {
        testLogger.info("Deploying Economy Chain")

        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!
                .readText()
                .replace(
                        "<string>$EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER</string>",
                        "<bytea>${BlockchainRid.ZERO_RID.toHex()}</bytea>"
                )
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
        chrAssetId = awaitQueryResult {
            node1.client(ecBrid).getAssetsByName(ASSET_NAME, null, null).data[0]["id"]?.asByteArray()
        }!!
    }

    @Test
    @Order(4)
    fun `Register FT accounts`() {
        testLogger.info("Registering FT accounts")

        // Register Alice account
        aliceAuthenticator = registerAccount(ecBrid, aliceKeyPair, "Alice")
        linkAccount(aliceAuthenticator, aliceEvmAddress, ecBrid)

        // Claim initial supply
        aliceAuthenticator.verifyOperationAuthFlags("faucet")
        aliceAuthenticator.transactionBuilder()
                .faucetOperation()
                .postTransactionUntilConfirmed("Claiming initial supply")

        val aliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        testLogger.info("Alice account balance is: $aliceBalance")
        assertThat(aliceBalance).isEqualTo(INITIAL_SUPPLY)
    }

    @Test
    @Order(5)
    fun `Deploy token chain EVM receiver`() {
        testLogger.info("Deploying EIF Event Receiver Chain for token chain")
        val gtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/evm_event_receiver_token_chain.xml")!!
                .readText()
        )

        node1.c0.transactionBuilder()
                .initEvmEventReceiverTokenChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add ${EVM_EVENT_RECEIVER_CHAIN_NAME}")

        val erRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_EVENT_RECEIVER_CHAIN_NAME }?.rid
        assertThat(erRid).isNotNull()
        eventReceiverBrid = BlockchainRid(erRid!!)

        testLogger.info { "${EVM_EVENT_RECEIVER_CHAIN_NAME} deployed: ${eventReceiverBrid}" }
    }

    @Test
    @Order(6)
    fun `Deploy token chain`() {
        testLogger.info("Deploying token chain")
        val gtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/token_chain.xml")!!
                .readText()
                .replace(
                        "<string>${EIF_TC_EVENT_RECEIVER_BRID_PLACEHOLDER}</string>",
                        "<bytea>${eventReceiverBrid.toHex()}</bytea>"
                )
                .replace("<bytea>00</bytea>", "<bytea>${ecBrid.toHex()}</bytea>")
        )

        node1.c0.transactionBuilder()
                .initTokenChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add ${TC_CHAIN_NAME}")

        val tcRid = node1.c0.getBlockchains(true).firstOrNull { it.name == TC_CHAIN_NAME }?.rid
        assertThat(tcRid).isNotNull()
        tcBrid = BlockchainRid(tcRid!!)

        node1.client(tcBrid, listOf(bobKeyPair)).transactionBuilder().initTokenChainOperation()
                .postTransactionUntilConfirmed("Init ${TC_CHAIN_NAME}")

        testLogger.info { "$TC_CHAIN_NAME deployed: $tcBrid" }

        // get tCHR assetId
        awaitQueryResult {
            node1.tc.getAssetsByName(ASSET_NAME, null, null).data[0]["id"]?.asByteArray()
        }!!
    }

    @Test
    @Order(7)
    fun `Perform cross-chain transfers between EC and TC`() {
        testLogger.info("Initializing cross chain transfer from EC to TC")
        val chromiaClientProvider = ChromiaClientProvider(ContainerClusterManagement(
                ClusterManagementImpl(node1.c0),
                mapOf(
                        systemCluster to listOf(node1.peerInfo(), node2.peerInfo(), node3.peerInfo())
                )
        ))
        val iccfProofTxMaterialBuilder = IccfProofTxMaterialBuilder(chromiaClientProvider)
        val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

        testLogger.info("Transfer tCHR to TC from EC")
        val initialEcAliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)

        val amount = BigInteger("100000000") // 100 tchr
        performCrossChainTransfer(iccfProofTxMaterialBuilder, merkleHashCalculator, aliceAuthenticator, ecBrid, tcBrid, amount)

        node1.client(tcBrid, listOf(aliceKeyPair)).transactionBuilder()
                .rasTransferFeeOperation(
                chrAssetId,
                AuthDescriptor(
                        AuthType.valueOf(aliceAuthenticator.authDescriptor.authType.name),
                        aliceAuthenticator.authDescriptor.args.asArray().toList(),
                        aliceAuthenticator.authDescriptor.rules),
                null
        ).registerAccountOperation()
                .postTransactionUntilConfirmed("Register account")
        aliceTcAuthenticator = FTAuthenticator(aliceKeyPair, node1.client(tcBrid, listOf(aliceKeyPair)))
        linkAccount(aliceTcAuthenticator, aliceEvmAddress, tcBrid)

        val afterTransferEcAliceBalance = node1.ec.getBalance(aliceAuthenticator.accountId)
        val afterTransferTcAliceBalance = node1.tc.getAssetBalance(aliceAuthenticator.accountId, chrAssetId)!!.amount
        assertThat(initialEcAliceBalance - afterTransferEcAliceBalance).isEqualTo(amount)
        assertThat(afterTransferTcAliceBalance).isEqualTo(amount.subtract(BigInteger("10000000")))
    }

    @Test
    @Order(8)
    fun `Add account creation chain`() {
        testLogger.info("Add container for account creation dapp")
        val containerVoterSet = "account_creation_dapp_container_vs"
        val containerName = "account_creation_dapp_container"
        node1.c0.transactionBuilder()
                .createVoterSetOperation(node1.providerPubkey, containerVoterSet, 1, listOf(node1.provider.pubKey.data), null)
                .proposeContainerOperation(node1.providerPubkey, systemCluster, containerName, containerVoterSet, "")
                .postTransactionUntilConfirmed("Add $containerName")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        testLogger.info("Deploying account creation dapp")
        deployDapp("test_token_chain_account_creation", containerName)
        accountCreationChainBrid = dapps["test_token_chain_account_creation"]!!
    }

    @Test
    @Order(9)
    fun `Propose token`() {

        aliceTcAuthenticator.transactionBuilder()
                .proposeTokenOperation("Test Token", "TT", 6, "http://wwww.icon.com",
                        listOf(MintingPolicy(
                                setOf(aliceAuthenticator.accountId.wrap()),
                                BigInteger.ZERO,
                                1000,
                                BigInteger.TEN,
                                true
                        )),
                        listOf(accountCreationChainBrid.data))
                .postTransactionUntilConfirmed("Propose new token")

        castVoteOnLatestProposal()

        val tokens = node1.tc.getAssetsByName("Test Token", null, null).data
        assertThat(tokens).hasSize(1)
        val testToken = tokens[0].asDict()
        testTokenAssetId = testToken["id"]!!.asByteArray()

        aliceTcAuthenticator.transactionBuilder()
                .mintTokenOperation(testTokenAssetId, BigInteger.TEN)
                .postTransactionUntilConfirmed("Mint 10 tokens")
        assertThat(node1.tc.getAssetBalance(aliceAuthenticator.accountId, testTokenAssetId)!!.amount)
                .isEqualTo(BigInteger.TEN)
    }

    @Test
    @Order(10)
    fun `Propose token bridge`() {
        val bridgeContract = WrappedByteArray.fromHex(bridgeAddress.substring(2))

        aliceTcAuthenticator.transactionBuilder()
                .proposeTokenBridgeOperation(testTokenAssetId, listOf(BridgeConfiguration(
                        evmContainerNetworkId,
                        bridgeContract,
                        WrappedByteArray.fromHex(testTokenAddress.substring(2)),
                        BridgeMode.foreign,
                        false
                ))).postTransactionUntilConfirmed("Propose token bridge")

        castVoteOnLatestProposal()

        val bridgeContracts = node1.tc.getBridgeContracts(evmContainerNetworkId)
        assertThat(bridgeContracts).hasSize(1)
        assertThat(bridgeContracts.first().contractAddress).isEqualTo(bridgeContract)

        testLogger.info { "Await query on event receiver to report new dynamic topic" }
        awaitQueryResult {
            val eventReceiverContracts = node1.client(eventReceiverBrid).query("eif.get_contracts", gtv("network_id" to gtv(evmContainerNetworkId)))
                    .asArray().map { WrappedByteArray.fromHex(it.asString().replace("0x", "")) }
            assertThat(eventReceiverContracts).hasSize(1)
            assertThat(eventReceiverContracts.first()).isEqualTo(bridgeContract)
        }
    }

    @Test
    @Order(11)
    fun `Make a deposit on new bridge`() {
        testLogger.info { "Deposit token on EVM" }

        // deposit on EVM
        bridge.deposit(Address(testToken.contractAddress), Uint256(depositAmount)).send()

        // check the balance on EVM
        val aliceBalance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, INITIAL_SUPPLY - depositAmount)

        // check the asset balance on Chromia
        awaitQueryResult {
            val balance = node1.tc.getAssetBalance(aliceTcAuthenticator.accountId, testTokenAssetId)
            assertThat(balance?.amount).isEqualTo(BigInteger.TEN.plus(depositAmount))
        }
    }

    @Test
    @Order(12)
    fun `Create a new account on token chain`() {
        val chromiaClientProvider = ChromiaClientProvider(ContainerClusterManagement(
                ClusterManagementImpl(node1.c0),
                mapOf(
                        systemCluster to listOf(node1.peerInfo(), node2.peerInfo(), node3.peerInfo())
                )
        ))
        val iccfProofTxMaterialBuilder = IccfProofTxMaterialBuilder(chromiaClientProvider)

        val newUser = cryptoSystem.generateKeyPair()

        val accountCreationTxRid = node1.client(accountCreationChainBrid, listOf(newUser)).transactionBuilder()
                .addOperation("create_token_chain_account", GtvObjectMapper.toGtvArray(AuthDescriptor(
                        AuthType.S,
                        listOf(
                                gtv(listOf(
                                        gtv("A"), gtv("T")
                                )),
                                gtv(newUser.pubKey.data)
                        ),
                        GtvNull)))
                .postTransactionUntilConfirmed("Creating account").txRid

        val accountCreationTx = GtvDecoder.decodeGtv(node1.client(accountCreationChainBrid).getTransaction(accountCreationTxRid))

        val accountCreationTxProof = awaitQueryResult {
            iccfProofTxMaterialBuilder.build(
                    accountCreationTxRid,
                    accountCreationTx.merkleHash(hashCalculator),
                    listOf(newUser.pubKey),
                    accountCreationChainBrid,
                    tcBrid,
                    iccfTxSigners = listOf(newUser)
            )
        }!!

        val initalBalance = node1.tc.getAssetBalance(aliceTcAuthenticator.accountId, chrAssetId)!!
        accountCreationTxProof.txBuilder
                .addOperation("ras_iccf", accountCreationTx, gtv(testTokenAssetId))
                .registerAccountOperation()
                .postTransactionUntilConfirmed("Registering account via ICCF proof")

        assertThat(node1.tc.getAccountById(gtv(newUser.pubKey.data).merkleHash(hashCalculator))).isNotNull()
        assertThat(node1.tc.getAssetBalance(aliceTcAuthenticator.accountId, chrAssetId)!!.amount)
                .isEqualTo(initalBalance.amount.subtract(BigInteger("10000000")))
    }

    private fun castVoteOnLatestProposal() {
        with(node1.client(tcBrid, listOf(bobKeyPair))) {
            val latestProposalId = getCommonProposalsRange(0, Long.MAX_VALUE, true).last().rowid

            transactionBuilder()
                    .makeCommonVoteOperation(PubKey(bobPubkey), latestProposalId, true)
                    .postTransactionUntilConfirmed("Voted in favour for proposal $latestProposalId")

        }
    }

    private fun registerAccount(blockchainRid: BlockchainRid, userKeyPair: KeyPair, username: String): FTAuthenticator {
        node1.client(blockchainRid, listOf(ecAdminKeyPair)).transactionBuilder().addNop()
                .registerAccountOperation(userKeyPair.pubKey)
                .postTransactionUntilConfirmed("Register $username account")

        return FTAuthenticator(userKeyPair, node1.client(blockchainRid, listOf(userKeyPair)))
    }

    private fun linkAccount(userAuthenticator: FTAuthenticator, userEvmAddress: ByteArray, brid: BlockchainRid) {
        val signature = getLinkEvmEoaAccountSignature(userEvmAddress, evmContainerCredentials.ecKeyPair, userAuthenticator.accountId, brid)

        userAuthenticator.client.transactionBuilder().addNop()
                .evmSignaturesOperation(listOf(userEvmAddress), listOf(signature))
                .ftAuthOperation(userAuthenticator.accountId, userAuthenticator.authDescriptor.id.data)
                .linkEvmEoaAccountOperation(userEvmAddress)
                .postTransactionUntilConfirmed("Link EVM account")
    }

    private fun getLinkEvmEoaAccountSignature(addressByteArray: ByteArray, evmKeyPair: ECKeyPair, accountId: ByteArray, brid: BlockchainRid): Signature {
        val opName = LINK_EVM_EOA_ACCOUNT
        val opArgs = gtv(listOf(gtv(addressByteArray)))

        val nonce = gtv(listOf(
                gtv(brid),
                gtv(opName),
                opArgs,
                gtv(0),
        )).merkleHash(hashCalculator)

        val template = node1.client(brid).getAuthMessageTemplate(opName, opArgs)
        val message = template.replace("{blockchain_rid}", brid.toHex().uppercase())
                .replace("{nonce}", nonce.toHex().uppercase())
                .replace("{account_id}", accountId.toHex())

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

    private fun performCrossChainTransfer(
            iccfProofTxMaterialBuilder: IccfProofTxMaterialBuilder,
            hashCalculator: GtvMerkleHashCalculator,
            sourceAccountAuthenticator: FTAuthenticator,
            sourceChain: BlockchainRid,
            destinationChain: BlockchainRid,
            amount: BigInteger = BigInteger.TEN
    ) {
        val initTransferTxRid = sourceAccountAuthenticator.transactionBuilder()
                .initTransferOperation(sourceAccountAuthenticator.accountId, chrAssetId, amount, listOf(destinationChain.data), Long.MAX_VALUE)
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
                .addOperation(APPLY_TRANSFER, initTransferTx, gtv(1), initTransferTx, gtv(1), gtv(0))
                .postTransactionUntilConfirmed("Apply transfer tx").txRid

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
                .addOperation(COMPLETE_TRANSFER, applyTransferTx, gtv(1))
                .postAwaitConfirmation()

    }
}
