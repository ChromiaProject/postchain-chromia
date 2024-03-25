package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.common.queries.getVoterSets
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.contracts.TestToken
import net.postchain.eif.contracts.TokenBridge
import net.postchain.eif.contracts.Validator
import net.postchain.eif.eif.evm.getAccountIdByEvmAddress
import net.postchain.eif.eif.evm.registerAccountOperation
import net.postchain.eif.eif.ft4.addNewEvmErc20Operation
import net.postchain.eif.eif.ft4.addNewTokenMappingOperation
import net.postchain.eif.lib.ft4.accounts.AuthDescriptor
import net.postchain.eif.lib.ft4.accounts.AuthType
import net.postchain.eif.lib.ft4.admin.registerAssetOperation
import net.postchain.eif.lib.ft4.assets.external.getAssetBalance
import net.postchain.eif.lib.ft4.assets.external.getAssetsByName
import net.postchain.eif.lib.ft4.auth.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
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
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.Contract
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.math.BigInteger

@Testcontainers
@DisableIfTestFails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1EventReceiverMixIT {

    companion object : ManagedModeBase() {

        private const val EVM_EVENT_RECEIVER_CHAIN_NAME = "evm_event_receiver_chain"
        private const val EVM_TOKEN_BRIDGE_CHAIN_NAME = "evm_token_bridge"
        private const val PROVIDER1_VS = "provider1_vs"
        private const val PROVIDER2_VS = "provider2_vs"

        private val evmContainerLogger = KotlinLogging.logger("EvmEventReceiver_EvmContainerLogger")
        private val node1Logger = KotlinLogging.logger("EvmEventReceiver_Node1Logger")
        private val node2Logger = KotlinLogging.logger("EvmEventReceiver_Node2Logger")
        private val node3Logger = KotlinLogging.logger("EvmEventReceiver_Node3Logger")
        override val logsSubdir = "evm_event_receiver"
        private val provider1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        lateinit var eventReceiverBrid: BlockchainRid
        lateinit var tokenBridgeBrid: BlockchainRid

        private val evmContainer: EvmContainer
        private val web3j: Web3j
        private val transactionManager: TransactionManager
        private val networkId = 1337L
        private val gasProvider = DefaultGasProvider()

        private lateinit var validator: Validator
        private lateinit var bridge: TokenBridge
        private lateinit var testToken: TestToken
        private lateinit var testTokenAddress: ByteArray

        private val tokenBridgeBinary = getBinaryFromArtifactResource("/artifacts/contracts/TokenBridge.sol/TokenBridge.json")
        private val testTokenBinary = getBinaryFromArtifactResource("/artifacts/contracts/token/TestToken.sol/TestToken.json")
        private val validatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/Validator.sol/Validator.json")

        // TODO: use getEvmAddress
        private val node0EvmAddress = Address("659e4a3726275edFD125F52338ECe0d54d15BD99")
        private val node1EvmAddress = Address("2c3fA9C9FC3C5CB2f9C09aF6f7214f64382eA086")

        // balances
        private val initialMint = BigInteger("FF".repeat(32), 16)
        private val depositNum = 5
        private val depositAmount = BigInteger("AA".repeat(16), 16)
        private val totalDepositedAmount = depositNum.toBigInteger() * depositAmount
        private val totalTransferAmount = BigInteger("1234567890ABCDEF", 16)

        private val snapshotHeights = mutableListOf<Long>()

        // users
        // - admin
        private val adminKeyPair = KeyPair.of( // node1
                "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57",
                "3132333435363738393031323334353637383930313233343536373839303131"
        )
        private val adminSigMaker = cryptoSystem.buildSigMaker(adminKeyPair)

        // - Alice
        private val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
        private val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
        private val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)
        private val aliceEvmAddressStr = "e105ba42b66d08ac7ca7fc48c583599044a6dab3"
        private val aliceEvmAddress = aliceEvmAddressStr.hexStringToByteArray()
        private lateinit var aliceAccountId: ByteArray

        // - Bob
        private val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
        private val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
        private val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)
        private val bobEvmAddressStr = "661683e5d36E83B38B1a20247ba6F5c410dC165d"
        private val bobEvmAddress = bobEvmAddressStr.hexStringToByteArray()
        private lateinit var bobAccountId: ByteArray

        private lateinit var userBalance: Uint256
        private lateinit var withdrawAmount: BigInteger
        private var accountNumber: Long = 0L
        private lateinit var authDescriptorId: Hash
        private lateinit var authId: Gtv
        private lateinit var assetId: ByteArray

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
                    .withEifEnv()
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix")
                    .withEifEnv()
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
            val artifactFile = Directory1EventReceiverMixIT::class.java.getResource(resourcePath)?.readText()
            val artifactJson = GsonBuilder().create().fromJson(artifactFile, JsonObject::class.java)
            return artifactJson.get("bytecode").asString
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

        // Adding provider2 as system
        testLogger.info("Add system provider provider2 its node")
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

        // Adding a dapp cluster
        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder().addNop()
                .createClusterOperation(node1.providerPubkey, "dapp_cluster", "SYSTEM_P", listOf(node1.providerPubkey))
                .createContainerOperation(node1.providerPubkey, "dapp_container", "dapp_cluster", 1, listOf(node1.providerPubkey))
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, "dapp_cluster")
                .postTransactionUntilConfirmed("dapp_cluster and dapp_container created")
    }

    @Test
    @Order(2)
    fun `Deploy contracts on EVM`() {
        testLogger.info { "Deploy contracts on EVM" }

        // Deploy validator contract
        val encodedConstructor = FunctionEncoder.encodeConstructor(listOf(DynamicArray(Address::class.java, node0EvmAddress)))
        validator = Contract.deployRemoteCall(Validator::class.java, web3j, transactionManager, gasProvider, validatorBinary, encodedConstructor).send()

        // Deploy token bridge contract
        bridge = Contract.deployRemoteCall(TokenBridge::class.java, web3j, transactionManager, gasProvider, tokenBridgeBinary, "").send().apply {
            initialize(Address(validator.contractAddress), Uint256(2)).send()
        }

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        testToken = Contract.deployRemoteCall(TestToken::class.java, web3j, transactionManager, gasProvider, testTokenBinary, "").send().apply {
            mint(Address(transactionManager.fromAddress), Uint256(initialMint)).send()
            approve(Address(bridge.contractAddress), Uint256(initialMint)).send()
        }
        testTokenAddress = testToken.contractAddress.substring(2).hexStringToByteArray()

        // Allow token
        bridge.allowToken(Address(testToken.contractAddress)).send()

        // Assert initial balance
        val balance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(initialMint, balance.value)
    }

    @Test
    @Order(3)
    fun `Deploy EVM Event Receiver chain`() {
        testLogger.info("Deploy EVM Event Receiver Chain")
        val gtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/evm_event_receiver.xml")!!.readText())

        node1.c0.transactionBuilder()
                .initEvmEventReceiverChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add $EVM_EVENT_RECEIVER_CHAIN_NAME")

        val tcRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_EVENT_RECEIVER_CHAIN_NAME }?.rid
        assertThat(tcRid).isNotNull()
        eventReceiverBrid = BlockchainRid(tcRid!!)

        testLogger.info { "$EVM_EVENT_RECEIVER_CHAIN_NAME deployed: $eventReceiverBrid" }
    }

    @Test
    @Order(4)
    fun `Deploy EVM Token Bridge dapp`() {
        testLogger.info("Deploy EVM Token Bridge dapp")
        deployDapp("evm_token_bridge", "dapp_container", assertSigners = arrayOf(node1), icmfReceiver = eventReceiverBrid.data)

        val brid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_TOKEN_BRIDGE_CHAIN_NAME }?.rid
        assertThat(brid).isNotNull()
        tokenBridgeBrid = BlockchainRid(brid!!)

        testLogger.info { "$EVM_TOKEN_BRIDGE_CHAIN_NAME deployed: $tokenBridgeBrid" }
    }

    @Test
    @Order(5)
    fun `Register FT accounts`() {
        testLogger.info { "Register FT accounts" }

        val tokenName = "Chromia"
        val tokenSymbol = "CHR"
        val tokenDecimal = 18L
        val tokenIconUrl = "https://chromaway.com/chr"

        node1.client(tokenBridgeBrid, signers = listOf(adminKeyPair)).transactionBuilder()
                .registerAssetOperation(tokenName, tokenSymbol, tokenDecimal, tokenIconUrl)
                .postTransactionUntilConfirmed("Register asset")

        assetId = awaitQueryResult {
            node1.client(tokenBridgeBrid).getAssetsByName(tokenName, 1L, null).data[0]["id"]?.asByteArray()
        }!!

        // Register evm account
        val aliceSig = Signature(
                "39b0c8c44a10d0fd70c0ed0e833cf6d93818ae1b10777857eb868516932796dc".hexStringToWrappedByteArray(),
                "44de8f297cce55c3da8401dd77269d0baf978f60e97ebc5717d4c8eeaed3bea9".hexStringToWrappedByteArray(),
                28L
        )
        val aliceAuth = AuthDescriptor(
                AuthType.S,
                listOf(
                        GtvArray(arrayOf(GtvFactory.gtv("A"), GtvFactory.gtv("T"))),
                        GtvFactory.gtv(alicePubkey)
                ),
                GtvNull
        )
        val bobSig = Signature(
                "8fa4216cd5979efdeb109e10f87225ea9579fd21289fac7f1410278554e79aff".hexStringToWrappedByteArray(),
                "442017757e4e627a98d40c89cdbdde4612251cc86acabba27cf1683cd1d7cb4c".hexStringToWrappedByteArray(),
                28L
        )
        val bobAuth = AuthDescriptor(
                AuthType.S,
                listOf(
                        GtvArray(arrayOf(GtvFactory.gtv("A"), GtvFactory.gtv("T"))),
                        GtvFactory.gtv(bobPubkey)
                ),
                GtvNull
        )

        node1.client(tokenBridgeBrid, signers = listOf(adminKeyPair)).transactionBuilder()
                .addNewEvmErc20Operation(networkId, testTokenAddress, tokenName, tokenSymbol, tokenDecimal)
                .addNewTokenMappingOperation(networkId, testTokenAddress, assetId)
                .postTransactionUntilConfirmed("Add ERC-20 token")
        node1.client(tokenBridgeBrid, signers = listOf(aliceKeyPair)).transactionBuilder()
                .registerAccountOperation(aliceEvmAddress, aliceAuth, aliceSig)
                .postTransactionUntilConfirmed("Register Alice account")
        node1.client(tokenBridgeBrid, signers = listOf(bobKeyPair)).transactionBuilder()
                .registerAccountOperation(bobEvmAddress, bobAuth, bobSig)
                .postTransactionUntilConfirmed("Register Bob account")

        awaitQueryResult {
            node1.client(tokenBridgeBrid).getAccountIdByEvmAddress(aliceEvmAddress)?.also {
                assertThat(it).isNotNull()
                aliceAccountId = it
            }
            node1.client(tokenBridgeBrid).getAccountIdByEvmAddress(bobEvmAddress)?.also {
                assertThat(it).isNotNull()
                bobAccountId = it
            }
        }
    }

    @Test
    @Order(6)
    fun `Deposit token on EVM`() {
        testLogger.info { "Deposit token on EVM" }

        // deposit on EVM
        for (i in 1..depositNum) {
            bridge.deposit(Address(testToken.contractAddress), Uint256(depositAmount)).send()
        }
        // check the balance on EVM
        userBalance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(userBalance.value, initialMint - totalDepositedAmount)

        // check the asset balance on Chromia
        awaitQueryResult {
            val balance = node1.client(tokenBridgeBrid).getAssetBalance(aliceAccountId, assetId)
            assertThat(balance?.amount).isEqualTo(totalDepositedAmount)
        }
        snapshotHeights.add(node1.client(tokenBridgeBrid).currentBlockHeight())

        // TODO: Skipping the `Check eif state for account as well` from the IT tests here
    }

}