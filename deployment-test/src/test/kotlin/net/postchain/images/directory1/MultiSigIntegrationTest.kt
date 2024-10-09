package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.economy_chain.getBalance
import net.postchain.chain0.economy_chain.initOperation
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.economy_chain_test_auth_server.registerMultisigAccountOperation
import net.postchain.chain0.economy_chain_test_claim_tchr.claimTestChrOperation
import net.postchain.chain0.lib.ft4.external.auth.ftAuthOperation
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.lib.ft4.external.accounts.getAccountMainAuthDescriptor
import net.postchain.eif.lib.ft4.external.assets.getAssetsByName
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigInteger

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MultiSigIntegrationTest {

    companion object : ManagedModeBase() {
        private val node1Logger = KotlinLogging.logger("MultiSig_Node1Logger")
        private val node2Logger = KotlinLogging.logger("MultiSig_Node2Logger")
        private val node3Logger = KotlinLogging.logger("MultiSig_Node3Logger")
        override val logsSubdir = "multisig"
        private val node1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        private const val EC_CHAIN_NAME = "economy_chain"
        private lateinit var assetId: ByteArray
        private const val ASSET_NAME = "tCHR"

        private val INITIAL_SUPPLY = BigInteger.valueOf(1_000_000_000L)

        private val ecAdminKeyPair = KeyPair.of(
                "02552192E2FA6F1C1229EB74FBDC9F27EEB87641BA11B29F9094D4F729C081AFA3",
                "E9CF8BC054D6F853FA9457D95EDBCA76EF52CEAD2913674031513FB015F5B5C0")

        private val alicePubkey = "038f888dec563b5bc253e87abc90afd26c3287021d10236ea19d248043dc39e0b8".hexStringToByteArray()
        private val alicePrivkey = "71b5b7f8de0661af934a5e4612f3d0ba183e639bdf4e7452fb6457ed3cfbc825".hexStringToByteArray()
        private val aliceKeyPair = KeyPair(alicePubkey, alicePrivkey)

        private val bobPubkey = "02E0A8A3C79C9F18B7CEAD2493435AC926B4A527EF670B873F5F1410084EFF9C80".hexStringToByteArray()
        private val bobPrivkey = "B31AB878C62B0E940B345C659A456D3573CF25960823C34C7BEEB5D1F813BEFD".hexStringToByteArray()
        private val bobKeyPair = KeyPair(bobPubkey, bobPrivkey)

        private val charliePubkey = "02620EB55BF0E3116F95D4D21771313AE5A477D5D166787DD8586D4413E3405D7E".hexStringToByteArray()
        private val charliePrivkey = "944D38EA36E0FA2D9862D77F99874D88FD172559E56913DA55578740573B19ED".hexStringToByteArray()
        private val charlieKeyPair = KeyPair(charliePubkey, charliePrivkey)

        init {


            // Nodes
            chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    node1KeyPair,
                    "config-mix"
            )

            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix"
            )

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

            removeSubnodeContainers()
            startNodesAndChain0()
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
    }

    @Test
    @Order(3)
    fun `Deploy Economy Chain`() {
        testLogger.info("Deploying Economy Chain")

        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!
                .readText()
                .replace(
                        "<string>EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER</string>",
                        "<bytea>1111111111111111111111111111111111111111111111111111111111111111</bytea>"
                )
        )

        node1.c0.transactionBuilder()
                .initEconomyChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(economyChainGtvConfig))
                .postTransactionUntilConfirmed("Add ${EC_CHAIN_NAME}")

        val ecRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EC_CHAIN_NAME }?.rid
        assertThat(ecRid).isNotNull()
        ecBrid = BlockchainRid(ecRid!!)

        testLogger.info { "${EC_CHAIN_NAME} deployed: $ecBrid" }

        node1.ec.transactionBuilder()
                .initOperation()
                .postTransactionUntilConfirmed("Init ${EC_CHAIN_NAME}")
        testLogger.info { "${EC_CHAIN_NAME} initialized" }

        // get tCHR assetId
        assetId = awaitQueryResult {
            node1.client(ecBrid).getAssetsByName(ASSET_NAME, null, null).data[0]["id"]?.asByteArray()
        }!!
    }

    @Test
    @Order(4)
    fun `FT multi-sig transaction`() {
        testLogger.info("FT multi-sig transaction")

        // Register multi-sig account
        val signers = listOf(aliceKeyPair, bobKeyPair)
        node1.client(ecBrid, listOf(ecAdminKeyPair)).transactionBuilder().addNop()
                .registerMultisigAccountOperation(signers.map { it.pubKey.data })
                .postTransactionUntilConfirmed("Register account")

        val alice = listOf(aliceKeyPair)
        val bob = listOf(bobKeyPair)
        val accountId = getAccountId(signers.map { it.pubKey })
        val accountMainAuthDescriptor = node1.client(ecBrid, alice).getAccountMainAuthDescriptor(accountId)
        // Claim initial supply - partial sign
        val partiallySignedTx = node1.client(ecBrid, alice)
                .transactionBuilder(alice, listOf(bobKeyPair.pubKey))
                .ftAuthOperation(accountId, accountMainAuthDescriptor.id.data)
                .claimTestChrOperation()
                .build()

        val signedTx = node1.client(ecBrid, bob)
                .transactionBuilder(bob).signTransaction(partiallySignedTx)

        node1.client(ecBrid, bob)
                .transactionBuilder().sendTransaction(signedTx)

        val accountBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(accountId)
        testLogger.info("Account balance is: $accountBalance")
        assertThat(accountBalance).isEqualTo(INITIAL_SUPPLY)
    }

    @Test
    @Order(5)
    fun `FT multi-sig transaction signing order`() {
        testLogger.info("FT multi-sig transaction signing order")

        // Register multi-sig account
        val signers = listOf(aliceKeyPair, bobKeyPair, charlieKeyPair)
        node1.client(ecBrid, listOf(ecAdminKeyPair)).transactionBuilder().addNop()
                .registerMultisigAccountOperation(signers.map { it.pubKey.data })
                .postTransactionUntilConfirmed("Register account")

        val alice = listOf(aliceKeyPair)
        val bob = listOf(bobKeyPair)
        val charlie = listOf(charlieKeyPair)
        val accountId = getAccountId(signers.map { it.pubKey })
        val accountMainAuthDescriptor = node1.client(ecBrid, alice).getAccountMainAuthDescriptor(accountId)
        // Claim initial supply - partial sign
        val signerTransactionCharlie = node1.client(ecBrid, charlie)
                .transactionBuilder(charlie, listOf(bobKeyPair.pubKey, aliceKeyPair.pubKey))
                .ftAuthOperation(accountId, accountMainAuthDescriptor.id.data)
                .claimTestChrOperation()
                .build()

        val signedTxAlice = node1.client(ecBrid, alice)
                .transactionBuilder(alice).signTransaction(signerTransactionCharlie)

        val signedTxBob = node1.client(ecBrid, bob)
                .transactionBuilder(bob).signTransaction(signedTxAlice)

        node1.client(ecBrid, bob)
                .transactionBuilder().sendTransaction(signedTxBob)

        val accountBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(accountId)
        testLogger.info("Account balance is: $accountBalance")
        assertThat(accountBalance).isEqualTo(INITIAL_SUPPLY)
    }

    private val hashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

    private fun getAccountId(signers: List<PubKey>) = GtvFactory.gtv(
            signers.map { it.data.toHex() }
                    .sorted()
                    .map { it.hexStringToByteArray() }
                    .map { GtvFactory.gtv(it) })
            .merkleHash(hashCalculator)
}


