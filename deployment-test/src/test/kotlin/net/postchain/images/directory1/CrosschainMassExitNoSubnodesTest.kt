@file:Suppress("DEPRECATION")

package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_container.createContainerWithResourceLimitsOperation
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TxRid
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.AccountStateMerkleProof
import net.postchain.eif.EifSignature
import net.postchain.eif.contracts.RecoveryContract
import net.postchain.eif.contracts.TestToken
import net.postchain.eif.contracts.TokenBridgeWithSnapshotWithdraw
import net.postchain.eif.contracts.Validator
import net.postchain.eif.encodeBlockHeaderDataForEVM
import net.postchain.eif.encodeSignatureWithV
import net.postchain.eif.getEthereumAddress
import net.postchain.eif.lib.hbridge.core.getRecoveryContract
import net.postchain.eif.lib.hbridge.core.getStateSlotIdsForAddress
import net.postchain.eif.lib.ft4.external.assets.getAssetBalance
import net.postchain.eif.lib.ft4.external.assets.getAssetsByName
import net.postchain.eif.lib.ft4.external.assets.transferOperation
import net.postchain.eif.lib.ft4.external.auth.ftAuthOperation
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.testdapps.lib.ft4.core.accounts.AuthDescriptor
import net.postchain.testdapps.lib.ft4.core.accounts.AuthType
import net.postchain.testdapps.lib.ft4.external.admin.registerAccountOperation
import net.postchain.testdapps.lib.hbridge.core.REGISTER_RECOVERY_CONTRACT
import net.postchain.testdapps.test_crosschain_massexit.bridge.initOperation
import net.postchain.testdapps.test_crosschain_massexit.dapp.initOperation
import net.postchain.testdapps.test_crosschain_massexit.dapp.registerRecoveryContractOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.tx.Contract
import java.math.BigInteger

@Suppress("LoggingSimilarMessage")
@Testcontainers
@DisableIfTestFails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CrosschainMassExitNoSubnodesTest : EvmTestBase("crosschain_massexit") {

    private val adminKeyPair = KeyPair.of(
            "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070",
            "0000000000000000000000000000000001000000000000000000000000000000"
    )

    // Postchain
    private val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    // Chains
    private val fooContainer = "fooContainer"
    private lateinit var bridgeBrid: BlockchainRid
    private lateinit var dappBrid: BlockchainRid

    // EIF
    private val tokenBridgeWithSnapshotWithdrawBinary = getBinaryFromArtifactResource("/artifacts/contracts/TokenBridgeWithSnapshotWithdraw.sol/TokenBridgeWithSnapshotWithdraw.json")
    private val recoveryBinary = getBinaryFromArtifactResource("/artifacts/contracts/RecoveryContract.sol/RecoveryContract.json")
    private lateinit var validator: Validator
    private lateinit var bridge: TokenBridgeWithSnapshotWithdraw
    private lateinit var bridgeAddress: String
    private lateinit var testToken: TestToken
    private lateinit var recoveryContract: RecoveryContract
    private lateinit var bridgeMassExitBlock: BlockDetail
    private lateinit var dappMassExitBlock: BlockDetail

    // EIF / balances
    private val initialSupply = BigInteger.valueOf(1_000_000_000L)
    private val depositAmount = BigInteger.valueOf(1000)
    private lateinit var assetId: ByteArray

    // Assets
    val decimals = 18
    val tokenName = "Chromia"
    val tokenSymbol = "CHR"
    val tokenDecimal = decimals.toLong()
    val tokenIconUrl = "https://chromaway.com/chr"

    inline val Int.chr: BigInteger get() = BigInteger(this.toString() + "0".repeat(decimals), 10)

    // EIF / users
    private lateinit var aliceBridgeAccount: FTAuthenticator
    private lateinit var aliceDappAccount: FTAuthenticator
    private lateinit var bobDappAccount: FTAuthenticator

    init {
        chain0Config = this::class.java.getResource("/directory1deployment/manager.xml")!!.readText()

        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-no-subnodes"
        ).withEifEnv()

        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-no-subnodes"
        )
                .withGenesisNode(node1)
                .withEifEnv()

        node3 = postchainServer("node3",
                provider3KeyPair,
                "config-no-subnodes"
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

        testLogger.info("Adding system providers provider2 and provider3 and their nodes")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .addNop()
                .postTransactionUntilConfirmed("System provider2 and provider3 registered, node2 and node3 added to the system cluster")

        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())

        // Add dapp container
        with(node1.c0) {
            transactionBuilder()
                    .createContainerWithResourceLimitsOperation(
                            node1.providerPubkey, fooContainer, systemCluster, 1,
                            listOf(node1.provider.pubKey.data), mapOf(ContainerResourceLimitType.container_units to 1)
                    )
                    .postTransactionUntilConfirmed("$fooContainer container created")
            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertThat(containers).isEqualTo(setOf(systemContainer, fooContainer))
            }
        }
    }

    @Test
    @Order(2)
    fun `Deploy validator, bridge, and token contracts`() {
        testLogger.info { "Deploy validator, bridge, and token contracts" }

        // Deploy validator contract
        val encodedConstructor = FunctionEncoder.encodeConstructor(listOf(
                DynamicArray(Address::class.java, node0EvmAddress, node1EvmAddress, node2EvmAddress)
        ))
        validator = Contract.deployRemoteCall(Validator::class.java, web3j, transactionManager, gasProvider, validatorBinary, encodedConstructor).send()

        // Deploy token bridge contract
        bridge = Contract.deployRemoteCall(TokenBridgeWithSnapshotWithdraw::class.java, web3j, transactionManager, gasProvider, tokenBridgeWithSnapshotWithdrawBinary, "").send().apply {
            initialize(Address(validator.contractAddress), Uint256(2)).send()
        }
        bridgeAddress = bridge.contractAddress

        // Deploy a test token contract
        testToken = Contract.deployRemoteCall(TestToken::class.java, web3j, transactionManager, gasProvider, testTokenBinary, "").send().apply {
            mint(Address(transactionManager.fromAddress), Uint256(initialSupply)).send()
            approve(Address(bridge.contractAddress), Uint256(initialSupply)).send()
        }

        // Allow token on the bridge
        bridge.allowToken(Address(testToken.contractAddress)).send()

        // Assert initial balance
        val balance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(initialSupply, balance.value)
    }

    @Test
    @Order(3)
    fun `Deploy bridge chain and register foreign asset`() {
        deployDapp("test_crosschain_massexit_bridge", fooContainer, assertSigners = arrayOf(node1, node2, node3))
        bridgeBrid = dapps["test_crosschain_massexit_bridge"]!!
        // set bridge chain brid on bridge contract
        bridge.setBlockchainRid(Bytes32(bridgeBrid.data)).send()

        // Register foreign asset
        testLogger.info { "Register foreign asset on bridge chain" }
        node1.client(bridgeBrid, listOf(adminKeyPair)).transactionBuilder()
                .initOperation(
                        tokenName, tokenSymbol, tokenDecimal, tokenIconUrl,
                        evmContainerNetworkId,
                        addressToByteArray(bridgeAddress), addressToByteArray(testToken.contractAddress)
                )
                .addNop()
                .postTransactionUntilConfirmed("Initialize bridge chain")

        assetId = awaitQueryResult {
            node1.client(bridgeBrid).getAssetsByName(tokenName, 10L, null).data.first()["id"]!!.asByteArray()
        }!!

        testLogger.info { "Foreign $tokenSymbol asset registered: ${assetId.toHex()}" }
    }

    @Test
    @Order(4)
    fun `Register Alice FT account on bridge chain`() {
        testLogger.info { "Registering Alice FT account on bridge chain" }

        // Register Alice account
        aliceBridgeAccount = registerAccount(node1, adminKeyPair, bridgeBrid, aliceKeyPair, "Alice")
        linkAccount(aliceBridgeAccount, aliceEvmCredentials, bridgeBrid)

        // Verify the balance
        val aliceBalance = getAssetBalance(aliceBridgeAccount)
        assertThat(aliceBalance).isNull()
    }

    @Test
    @Order(5)
    fun `Deploy recovery contract for dapp chain`() {
        testLogger.info { "Deploy recovery contract for dapp chain" }

        // Deploy and configure the recovery contract
        recoveryContract = Contract.deployRemoteCall(RecoveryContract::class.java, web3j, transactionManager, gasProvider, recoveryBinary, "").send().apply {
            initialize(Address(validator.contractAddress)).send()
        }
        recoveryContract.allowToken(Address(testToken.contractAddress)).send()
    }

    @Test
    @Order(6)
    fun `Deploy dapp chain and register cross-chain asset`() {
        deployDapp("test_crosschain_massexit_dapp", fooContainer, assertSigners = arrayOf(node1, node2, node3))
        dappBrid = dapps["test_crosschain_massexit_dapp"]!!
        // set dapp chain brid on recovery contract
        recoveryContract.setBlockchainRid(Bytes32(dappBrid.data)).send()

        testLogger.info { "Register cross-chain asset on dapp chain" }
        node1.client(dappBrid).transactionBuilder()
                .initOperation(
                        assetId, bridgeBrid.data,
                        evmContainerNetworkId,
                        addressToByteArray(recoveryContract.contractAddress),
                        addressToByteArray(testToken.contractAddress)
                )
                .addNop()
                .postTransactionUntilConfirmed("Initialize dapp chain")

        val crosschainAssetId = awaitQueryResult {
            node1.client(bridgeBrid).getAssetsByName(tokenName, 10L, null).data.first()["id"]!!.asByteArray()
        }

        testLogger.info { "Cross-chain $tokenSymbol asset registered: ${crosschainAssetId?.toHex()}" }
    }

    @Test
    @Order(7)
    fun `Register Alice and Bob FT accounts on dapp chain`() {
        testLogger.info { "Registering Alice and Bob FT accounts on dapp chain" }

        // Register Alice account and verify the balance
        aliceDappAccount = registerAccount(node1, adminKeyPair, dappBrid, aliceKeyPair, "Alice")
        linkAccount(aliceDappAccount, aliceEvmCredentials, dappBrid)
        val aliceBalance = getAssetBalance(aliceDappAccount)
        assertThat(aliceBalance).isNull()

        // Register Bob account and verify the balance
        bobDappAccount = registerAccount(node1, adminKeyPair, dappBrid, bobKeyPair, "Bob")
        linkAccount(bobDappAccount, bobEvmCredentials, dappBrid)
        val bobBalance = getAssetBalance(bobDappAccount)
        assertThat(bobBalance).isNull()
    }

    @Test
    @Order(8)
    fun `Register recovery contract of dapp chain on bridge chain`() {
        testLogger.info { "Register recovery contract of dapp chain on dapp chain" }

        // Register recovery contract on the dapp chain
        val txToProveBuilder = aliceDappAccount.client.transactionBuilder(signers = emptyList())
                .registerRecoveryContractOperation(evmContainerNetworkId, addressToByteArray(recoveryContract.contractAddress))
                .addNop()
        txToProveBuilder.postTransactionUntilConfirmed("Register recovery contract on dapp chain")
        val txToProve = txToProveBuilder.finish().buildGtx()

        val iccfMaterial = IccfProofTxMaterialBuilder(buildChromiaClientProvider()).build(
                TxRid(txToProve.calculateTxRid(hashCalculator).toHex()),
                txToProve.toGtv().merkleHash(hashCalculator),
                dappBrid,
                bridgeBrid,
                forceIntraNetworkIccfOperation = true
        )
        val actualTxToProve = iccfMaterial.updatedTx ?: txToProve

        // Register the recovery contract on bridge chain
        testLogger.info { "Register recovery contract of dapp chain on bridge chain" }
        iccfMaterial.txBuilder
                .addOperation(REGISTER_RECOVERY_CONTRACT, actualTxToProve.toGtv(), gtv(0L))
                .postTransactionUntilConfirmed("Register recovery contract on bridge chain")

        // Assert that recover contract is set
        awaitQueryResult {
            val actual = aliceBridgeAccount.client.getRecoveryContract(dappBrid, evmContainerNetworkId)
            assertNotNull(actual)
            assertThat(actual!!).isEqualTo(addressToByteArray(recoveryContract.contractAddress))
        }
    }

    /**
     * Test cases visualization for cross-chain and on-chain token transfers between Alice and Bob.
     *
     * ```
     *     Bridge Chain                      Dapp Chain
     *     +----------------+               +----------------+
     *     |    Alice       |   (1) 900     |    Alice       |
     *     | Balance: 1000  |    =====>     | Balance: 900   |
     *     +----------------+               +----------------+
     *             |                              |
     *             |                              |  (2) 800
     *             |                              v
     *             |                        +----------------+
     *             |                        |     Bob        |
     *             |                        | Balance: 800   |
     *             |                        +----------------+
     *             |                              |
     *             |                              |  (3) 700
     *             |                              v
     *             |                        +----------------+
     *             |                        |    Alice       |
     *             |                        | Balance: 800   |
     *             |                        +----------------+
     *             |                              |
     *     +----------------+               +----------------+
     *     |    Alice       |   <=====      |    Alice       |
     *     | Balance: 700   |   (4) 600     | Balance: 200   |
     *     +----------------+               +----------------+
     *
     * Legend:
     * (1) Alice transfers 900 tokens from bridge to dapp chain
     * (2) Alice transfers 800 tokens to Bob on dapp chain
     * (3) Bob transfers 700 tokens back to Alice on dapp chain
     * (4) Alice transfers 600 tokens from dapp chain to bridge chain
     * ```
     */

    @Test
    @Order(9)
    fun `Alice bridges tokens from EVM to Chromia`() {
        testLogger.info { "Alice bridges tokens from EVM to Chromia" }

        // Deposit on EVM
        bridge.deposit(Address(testToken.contractAddress), Uint256(depositAmount)).send()

        // Check the balance on EVM
        val aliceBalance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, initialSupply - depositAmount)

        // Check the asset balance on bridge chain
        awaitQueryResult {
            val balance = getAssetBalance(aliceBridgeAccount)
            assertThat(balance).isEqualTo(depositAmount)
            testLogger.info("Alice account bridge balance is: $balance $tokenSymbol")
        }
    }

    @Test
    @Order(10)
    fun `Alice transfers tokens from bridge to dapp chain`() {
        testLogger.info { "Alice transfers 900 $tokenSymbol from bridge to dapp chain" }

        performCrossChainTransfer(
                node1,
                IccfProofTxMaterialBuilder(buildChromiaClientProvider()),
                merkleHashCalculator,
                aliceBridgeAccount,
                bridgeBrid,
                dappBrid,
                900.toBigInteger(),
                assetId
        )

        // Check the balances
        val (bridgeBalance, dappBalance) = awaitQueryResult {
            val bridgeBalance = getAssetBalance(aliceBridgeAccount)
            assertThat(bridgeBalance).isEqualTo(depositAmount - 900.toBigInteger())
            val dappBalance = getAssetBalance(aliceDappAccount)
            assertThat(dappBalance).isEqualTo(900.toBigInteger())
            bridgeBalance to dappBalance
        }!!
        testLogger.info { "Alice bridge account balance is: $bridgeBalance $tokenSymbol" }
        testLogger.info { "Alice dapp account balance is: $dappBalance $tokenSymbol" }
    }

    @Test
    @Order(11)
    fun `Alice transfers tokens to Bob, then Bob returns some change, on dapp chain`() {
        testLogger.info { "Alice transfers 800 $tokenSymbol to Bob, then Bob returns 700 $tokenSymbol, on dapp chain" }

        // Alice -> Bob -> Alice
        transfer(aliceDappAccount, bobDappAccount, assetId, 800.toBigInteger())
        transfer(bobDappAccount, aliceDappAccount, assetId, 700.toBigInteger())

        // Check the balances
        val (aliceBalance, bobBalance) = awaitQueryResult {
            val aliceBalance = getAssetBalance(aliceDappAccount)
            assertThat(aliceBalance).isEqualTo(800.toBigInteger())
            val bobBalance = getAssetBalance(bobDappAccount)
            assertThat(bobBalance).isEqualTo(100.toBigInteger())
            aliceBalance to bobBalance
        }!!
        testLogger.info { "Alice dapp account balance is: $aliceBalance $tokenSymbol" }
        testLogger.info { "Bob dapp account balance is: $bobBalance $tokenSymbol" }
    }

    @Test
    @Order(13)
    fun `Trigger mass exit on bridge chain`() {
        testLogger.info { "Trigger Mass Exit on bridge chain" }

        val lastBlockHeight = aliceBridgeAccount.client.currentBlockHeight() - 1
        bridgeMassExitBlock = aliceBridgeAccount.client.blockAtHeight(lastBlockHeight)!!
        val evmBlockHeader = encodeBlockHeaderDataForEVM(
                bridgeMassExitBlock.rid.data,
                BaseBlockHeader(bridgeMassExitBlock.header.data, merkleHashCalculator).blockHeaderRec
        )

        // Retrieve extraProofData from stateProof (see `HBridgeForeignModeIT` in the `postchain-eif` repo)
        val stateSlotIds = aliceBridgeAccount.client.getStateSlotIdsForAddress(addressToByteArray(recoveryContract.contractAddress), evmContainerNetworkId)
        val stateProof = getAccountStateMerkleProof(aliceBridgeAccount.client, lastBlockHeight, stateSlotIds.first())

        val evmSignatures = BaseBlockWitness.fromBytes(bridgeMassExitBlock.witness.data).getSignatures().map {
            EifSignature(
                    sig = encodeSignatureWithV(bridgeMassExitBlock.rid.data, it),
                    pubkey = getEthereumAddress(it.subjectID)
            )
        }.sortedBy { Address(it.pubkey.toHex()).toUint().value }

        bridge.triggerMassExit(
                evmBlockHeader.web3BlockHeader(),
                evmSignatures.web3Signatures(),
                evmSignatures.web3Signers(),
                stateProof.web3ExtraProofData()
        ).send()
    }

    @Test
    @Order(14)
    fun `Trigger mass exit on dapp chain`() {
        testLogger.info { "Trigger Mass Exit on dapp chain" }

        val lastBlockHeight = aliceDappAccount.client.currentBlockHeight() - 1
        dappMassExitBlock = aliceDappAccount.client.blockAtHeight(lastBlockHeight)!!
        val evmBlockHeader = encodeBlockHeaderDataForEVM(
                dappMassExitBlock.rid.data,
                BaseBlockHeader(dappMassExitBlock.header.data, merkleHashCalculator).blockHeaderRec
        )

        // Retrieve extraProofData from stateProof (see `HBridgeForeignModeIT` in the `postchain-eif` repo)
        val stateSlotIds = aliceDappAccount.client.getStateSlotIdsForAddress(aliceEvmAddress, evmContainerNetworkId)
        val stateProof = getAccountStateMerkleProof(aliceDappAccount.client, lastBlockHeight, stateSlotIds.first())

        val evmSignatures = BaseBlockWitness.fromBytes(dappMassExitBlock.witness.data).getSignatures().map {
            EifSignature(
                    sig = encodeSignatureWithV(dappMassExitBlock.rid.data, it),
                    pubkey = getEthereumAddress(it.subjectID)
            )
        }.sortedBy { Address(it.pubkey.toHex()).toUint().value }

        recoveryContract.triggerMassExit(
                evmBlockHeader.web3BlockHeader(),
                evmSignatures.web3Signatures(),
                evmSignatures.web3Signers(),
                stateProof.web3ExtraProofDataForRecoveryContract()
        ).send()
    }

    @Test
    @Order(15)
    fun `Transfer tokens from bridge contract to recovery contract of dapp chain after mass exit`() {
        testLogger.info { "Transfer tokens from bridge contract to recovery contract of dapp chain" }

        // Get dapp chain account state
        val stateSlotIds = aliceBridgeAccount.client.getStateSlotIdsForAddress(addressToByteArray(recoveryContract.contractAddress), evmContainerNetworkId)
        val stateProof = getAccountStateMerkleProof(aliceBridgeAccount.client, bridgeMassExitBlock.height, stateSlotIds.first())

        // Withdraw tokens to recovery contract
        bridge.withdrawBySnapshot(
                stateProof.web3StateData(),
                stateProof.web3StateProof()
        ).send()

        // Verify the balance of the recovery contract
        val recoveryContractBalance = testToken.balanceOf(Address(recoveryContract.contractAddress)).send()
        assertThat(recoveryContractBalance.value).isEqualTo(900.toBigInteger())
    }

    @Test
    @Order(16)
    fun `Withdraw tokens from recovery contract of dapp chain after mass exit using snapshot`() {
        testLogger.info { "Alice withdraws tokens from recovery contract of dapp chain after mass exit using snapshot" }

        val lastBlockHeight = aliceDappAccount.client.currentBlockHeight() - 1

        // Get Alice account state on the dapp chain
        val aliceStateSlotIds = aliceDappAccount.client.getStateSlotIdsForAddress(addressToByteArray(aliceEvmAddressStr), evmContainerNetworkId)
        val stateProof = getAccountStateMerkleProof(aliceDappAccount.client, lastBlockHeight, aliceStateSlotIds.first())

        // Withdraw tokens from recovery contract
        recoveryContract.withdrawBySnapshot(
                stateProof.web3StateData(),
                stateProof.web3StateProofForRecoveryContract()
        ).send()

        // Check the balance on EVM
        val aliceBalance = testToken.balanceOf(Address(aliceEvmAddressStr)).send()
        assertEquals(aliceBalance.value, initialSupply - depositAmount + 800.toBigInteger())
    }

    private fun registerAccount(
            node: PostchainContainer,
            adminKeyPair: KeyPair,
            bcRid: BlockchainRid,
            userKeyPair: KeyPair,
            username: String,
    ): FTAuthenticator {

        val auth = AuthDescriptor(
                AuthType.S,
                listOf(GtvArray(arrayOf(gtv("A"), gtv("T"))), gtv(userKeyPair.pubKey.data)),
                GtvNull
        )

        node.client(bcRid, listOf(adminKeyPair)).transactionBuilder()
                .registerAccountOperation(auth)
                .addNop()
                .postTransactionUntilConfirmed("Register $username FT4 account")

        return FTAuthenticator(userKeyPair, node1.client(bcRid, listOf(userKeyPair)), username)
    }

    private fun transfer(from: FTAuthenticator, to: FTAuthenticator, assetId: ByteArray, amount: BigInteger) {
        from.client.transactionBuilder()
                .ftAuthOperation(from.accountId, from.authDescriptor.id.data)
                .transferOperation(to.accountId, assetId, amount)
                .addNop()
                .postTransactionUntilConfirmed("${from.username} transfers $amount $tokenSymbol to ${to.username}")
    }

    private fun getAssetBalance(userAccount: FTAuthenticator): BigInteger? =
            userAccount.client.getAssetBalance(userAccount.accountId, assetId)?.amount

    private fun addressToByteArray(evmAddress: String): ByteArray = evmAddress.substringAfter("0x").hexStringToByteArray()

    private fun getAccountStateMerkleProof(client: PostchainClient, blockHeight: Long, accountNumber: Long) = client.query(
            "get_account_state_merkle_proof",
            gtv(
                    "blockHeight" to gtv(blockHeight),
                    "accountNumber" to gtv(accountNumber)
            )
    ).toObject<AccountStateMerkleProof>()

    private fun buildChromiaClientProvider() = ChromiaClientProvider(
            ContainerClusterManagement(
                    ClusterManagementImpl(node1.c0),
                    mapOf(systemCluster to listOf(node1.peerInfo(), node2.peerInfo(), node3.peerInfo()))
            )
    )

    fun ByteArray.web3BlockHeader() = DynamicBytes(this)
    fun List<EifSignature>.web3Signatures() = DynamicArray(DynamicBytes::class.java, this.map { DynamicBytes(it.sig) })
    fun List<EifSignature>.web3Signers() = DynamicArray(Address::class.java, this.map { Address(it.pubkey.toHex()) })
    fun AccountStateMerkleProof.web3ExtraProofData() = TokenBridgeWithSnapshotWithdraw.ExtraProofData(
            DynamicBytes(extraMerkleProof!!.leaf),
            Bytes32(extraMerkleProof!!.hashedLeaf),
            Uint256(extraMerkleProof!!.position),
            Bytes32(extraMerkleProof!!.extraRoot),
            DynamicArray(Bytes32::class.java, extraMerkleProof!!.extraMerkleProofs.map { Bytes32(it) })
    )

    fun AccountStateMerkleProof.web3ExtraProofDataForRecoveryContract() = RecoveryContract.ExtraProofData(
            DynamicBytes(extraMerkleProof!!.leaf),
            Bytes32(extraMerkleProof!!.hashedLeaf),
            Uint256(extraMerkleProof!!.position),
            Bytes32(extraMerkleProof!!.extraRoot),
            DynamicArray(Bytes32::class.java, extraMerkleProof!!.extraMerkleProofs.map { Bytes32(it) })
    )

    fun AccountStateMerkleProof.web3StateData() = DynamicBytes(stateData)
    fun AccountStateMerkleProof.web3StateProof() = TokenBridgeWithSnapshotWithdraw.Proof(
            Bytes32(stateProof!!.leaf),
            Uint256(stateProof!!.position),
            DynamicArray(Bytes32::class.java, stateProof!!.merkleProofs.map { Bytes32(it) })
    )

    fun AccountStateMerkleProof.web3StateProofForRecoveryContract() = RecoveryContract.Proof(
            Bytes32(stateProof!!.leaf),
            Uint256(stateProof!!.position),
            DynamicArray(Bytes32::class.java, stateProof!!.merkleProofs.map { Bytes32(it) })
    )
}