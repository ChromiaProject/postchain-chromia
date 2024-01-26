package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainerData
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.economy_chain.TicketState
import net.postchain.chain0.economy_chain.createContainerOperation
import net.postchain.chain0.economy_chain.getBalance
import net.postchain.chain0.economy_chain.getCreateContainerTicketByTransaction
import net.postchain.chain0.economy_chain.getLeasesByAccount
import net.postchain.chain0.economy_chain.getPoolBalance
import net.postchain.chain0.economy_chain.getProviderAccountId
import net.postchain.chain0.economy_chain.getUpgradeContainerTicketByTransaction
import net.postchain.chain0.economy_chain.initOperation
import net.postchain.chain0.economy_chain.registerAccountOperation
import net.postchain.chain0.economy_chain.registerProviderAccountOperation
import net.postchain.chain0.economy_chain.transferToPoolOperation
import net.postchain.chain0.economy_chain.upgradeContainerOperation
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.model.ContainerState
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
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
class Directory1EconomyChainMixIT {

    private val ecAdminKeyPair = KeyPair.of(
            "02552192E2FA6F1C1229EB74FBDC9F27EEB87641BA11B29F9094D4F729C081AFA3",
            "E9CF8BC054D6F853FA9457D95EDBCA76EF52CEAD2913674031513FB015F5B5C0")
    private val PostchainContainer.ecAdmin get() = client(ecBrid, listOf(ecAdminKeyPair))
    private val PostchainContainer.ec get() = client(ecBrid)
    private val ecAdminSigMaker = cryptoSystem.buildSigMaker(ecAdminKeyPair)

    private val userAccountOwnerKeys = KeyPair.of(
            "039BEA5DFA1F22D16EF702995794A0082CC358050CDBEAA1E50E7AC067BA1B6160",
            "A550EB5580B4DD1A972954CEC39679E4C0C3E82FF2A3510F3A931CE0DB12A567")
    private val ecUserAccountSigMaker = cryptoSystem.buildSigMaker(userAccountOwnerKeys)

    companion object : ManagedModeBase() {

        private const val EC_NAME = "economy_chain"
        private const val APP_CLUSTER = "appCluster"
        private const val CONTAINER_UNITS = 2L
        private const val CLUSTER_CLASS = ""
        private const val DURATION_WEEKS = 1L
        private const val EXTRA_STORAGE_GIB = 0L

        private val node1Logger = KotlinLogging.logger("TC_Node1Logger")
        private val node2Logger = KotlinLogging.logger("TC_Node2Logger")
        private val node3Logger = KotlinLogging.logger("TC_Node3Logger")

        private val node1KeyPair = KeyPair.of(
                "03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05",
                "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114")

        lateinit var ecBrid: BlockchainRid
        lateinit var userClient: PostchainClient
        lateinit var userAuthenticator: FTAuthenticator
        lateinit var containerName: String

        init {
            chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()

            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    node1KeyPair,
                    "config-mix")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-mix")
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

            removeSubnodeContainers()
            startNodesAndChain0()
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
    fun `Add new cluster`() {
        testLogger.info("Adding cluster")
        with(node1.c0) {
            transactionBuilder()
                    .createClusterOperation(node1.providerPubkey, APP_CLUSTER, "SYSTEM_P", listOf(node1.providerPubkey))
                    .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 3)
                    .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, APP_CLUSTER)
                    .postTransactionUntilConfirmed("$APP_CLUSTER cluster created")
        }
    }

    @Test
    @Order(3)
    fun `Add Economy Chain`() {
        testLogger.info("Adding Economy Chain")
        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!.readText())

        node1.c0.transactionBuilder()
                .initEconomyChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(economyChainGtvConfig))
                .postTransactionUntilConfirmed("Add $EC_NAME")

        val tcRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EC_NAME }?.rid
        assertThat(tcRid).isNotNull()
        ecBrid = BlockchainRid(tcRid!!)

        testLogger.info { "$EC_NAME deployed: $ecBrid" }

        node1.ec.transactionBuilder()
                .initOperation()
                .postTransactionUntilConfirmed("Init $EC_NAME")
        testLogger.info { "$EC_NAME initialized" }
    }

    @Test
    @Order(4)
    fun `Register accounts`() {
        testLogger.info("Register accounts")

        // Register provider account
        node1.ec.transactionBuilder()
                .registerProviderAccountOperation(node1KeyPair.pubKey.data)
                .postTransactionUntilConfirmed("Register provider account")
        val accountId = node1.ec.getProviderAccountId(node1KeyPair.pubKey.data)
        assertThat(accountId).isNotNull()
        testLogger.info("Provider account id ${accountId?.toHex()}")
        assertThat(node1.ec.getBalance(accountId!!)).isEqualTo(BigInteger.ZERO)

        // Register a new user account
        node1.ecAdmin.transactionBuilder()
                .registerAccountOperation(userAccountOwnerKeys.pubKey)
                .sign(ecAdminSigMaker)
                .postTransactionUntilConfirmed("Register user account")

        userClient = node1.client(ecBrid, listOf(userAccountOwnerKeys))
        userAuthenticator = FTAuthenticator(userClient).apply { init() }

        val userAccountBalance = userClient.getBalance(userAuthenticator.accountId)
        testLogger.info("user account balance is: $userAccountBalance")
        assertThat(userAccountBalance).isEqualTo(BigInteger.valueOf(1000000000))
    }

    @Test
    @Order(5)
    fun `Test pool account`() {
        testLogger.info("Test pool account")
        val poolBalance = node1.ec.getPoolBalance()
        testLogger.info("poolBalance is: $poolBalance")
        assertThat(poolBalance).isEqualTo(BigInteger.ZERO)

        // Transfer funds to the pool account
        userAuthenticator.verifyOperationAuthFlags("transfer_to_pool")
        userClient.transactionBuilder()
                .also { userAuthenticator.ftAuth(it) }
                .transferToPoolOperation(BigInteger.valueOf(1000))
                .sign(ecUserAccountSigMaker)
                .postTransactionUntilConfirmed("Transfer to pool")

        testLogger.info("poolBalance after transfer is: ${userClient.getPoolBalance()}")
        assertThat(userClient.getPoolBalance()).isEqualTo(BigInteger.valueOf(1000))
    }

    @Test
    @Order(6)
    fun `Create container`() {
        testLogger.info("Create container")
        userAuthenticator.verifyOperationAuthFlags("create_container")
        val tcRid = userClient.transactionBuilder()
                .also { userAuthenticator.ftAuth(it) }
                .createContainerOperation(node1.provider.pubKey.data, CONTAINER_UNITS, CLUSTER_CLASS, DURATION_WEEKS, EXTRA_STORAGE_GIB, APP_CLUSTER, true)
                .sign(ecUserAccountSigMaker)
                .postTransactionUntilConfirmed("Create Container")
                .txRid

        awaitUntilAsserted {
            val ticket = userClient.getCreateContainerTicketByTransaction(tcRid.rid.hexStringToByteArray())
            assertThat(ticket).isNotNull()
            assertThat(ticket!!.state).isEqualTo(TicketState.SUCCESS)
        }

        val leaseDataList = userClient.getLeasesByAccount(userAuthenticator.accountId)
        assertThat(leaseDataList.size).isEqualTo(1)
        val leaseData = leaseDataList[0]
        assertThat(leaseData.clusterName).isEqualTo(APP_CLUSTER)
        assertThat(leaseData.containerUnits).isEqualTo(CONTAINER_UNITS)
        assertThat(leaseData.extraStorageGib).isEqualTo(EXTRA_STORAGE_GIB)
        assertThat(leaseData.expired).isFalse()
        assertThat(leaseData.autoRenew).isTrue()

        containerName = leaseData.containerName
        val containerData = node1.c0.getContainerData(leaseData.containerName)
        assertThat(containerData).isNotNull()
        assertThat(containerData.cluster).isEqualTo(APP_CLUSTER)
        assertThat(containerData.proposedByPubkey).isEqualTo(node1KeyPair.pubKey.wData)
        assertThat(containerData.state).isEqualTo(ContainerState.RUNNING)

        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(CONTAINER_UNITS)
    }

    @Test
    @Order(7)
    fun `Upgrade container`() {
        testLogger.info("Upgrade container")
        userAuthenticator.verifyOperationAuthFlags("upgrade_container")
        val tcRid = userClient.transactionBuilder()
                .also { userAuthenticator.ftAuth(it) }
                .upgradeContainerOperation(containerName, CONTAINER_UNITS + 1, CLUSTER_CLASS, EXTRA_STORAGE_GIB, APP_CLUSTER)
                .sign(ecUserAccountSigMaker)
                .postTransactionUntilConfirmed("Upgrade Container")
                .txRid

        awaitUntilAsserted {
            val ticket = userClient.getUpgradeContainerTicketByTransaction(tcRid.rid.hexStringToByteArray())
            assertThat(ticket).isNotNull()
            assertThat(ticket!!.state).isEqualTo(TicketState.SUCCESS)
        }

        val leaseDataList = userClient.getLeasesByAccount(userAuthenticator.accountId)
        assertThat(leaseDataList.size).isEqualTo(1)
        val leaseData = leaseDataList[0]
        assertThat(leaseData.clusterName).isEqualTo(APP_CLUSTER)
        assertThat(leaseData.containerUnits).isEqualTo(CONTAINER_UNITS + 1)

        val containerLimits = node1.c0.nmGetContainerLimits(leaseData.containerName)
        assertThat(containerLimits["container_units"]).isEqualTo(CONTAINER_UNITS + 1)
    }
}