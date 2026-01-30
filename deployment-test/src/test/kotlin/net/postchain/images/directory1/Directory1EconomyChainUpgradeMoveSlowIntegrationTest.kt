@file:Suppress("DEPRECATION")

package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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
import net.postchain.chain0.economy_chain.getTagByName
import net.postchain.chain0.economy_chain.getUpgradeContainerTicketByTransaction
import net.postchain.chain0.economy_chain.initOperation
import net.postchain.chain0.economy_chain.upgradeContainerOperation
import net.postchain.chain0.economy_chain_in_directory_chain.initEconomyChainOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ContainerState
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.awaitQueryResult
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.lib.ft4.external.assets.getAssetsByName
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigInteger

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1EconomyChainUpgradeMoveSlowIntegrationTest : Directory1TestBase("container-upgrade-move") {

    companion object {

        const val EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER = "EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER"
        const val EIF_EC_STRATEGY_SENDER_BLOCKCHAIN = "EIF_EC_STRATEGY_SENDER_BLOCKCHAIN"

        const val ASSET_NAME = "tCHR"

        const val APP_CLUSTER1 = "appCluster1"
        const val APP_CLUSTER2 = "appCluster2"
        const val APP_CLUSTER_TAG = "appClusterTag"
        const val CONTAINER_UNITS = 2L
        const val DURATION_WEEKS = 1L
        const val EXTRA_STORAGE_GIB = 0L
        const val SCU_PRICE = 1L
        const val EXTRA_STORAGE_PRICE = 1L
        const val EXTRA_COMPUTE_PRICE = 0L
    }

    lateinit var containerName: String
    lateinit var dappBrid: BlockchainRid
    lateinit var CAC1: BlockchainRid
    lateinit var CAC2: BlockchainRid

    // EIF / balances
    private val initialSupply = BigInteger.valueOf(1_000_000_000L)
    private lateinit var assetId: ByteArray

    // EIF / users
    private lateinit var aliceAuthenticator: FTAuthenticator

    init {
        // Nodes
        chain0Config = this::class.java.getResource("/directory1deployment/mainnet.xml")!!.readText()
        node1 = postchainServerWithSubnodes("node1",
                provider1KeyPair,
                "config-all-subnodes"
        )
                .withEnv("POSTCHAIN_SUBNODE_IDLE_TIMEOUT_MS", 300_000.toString())

        node2 = postchainServerWithSubnodes("node2",
                provider2KeyPair,
                "config-all-subnodes"
        )
                .withGenesisNode(node1)
                .withEnv("POSTCHAIN_SUBNODE_IDLE_TIMEOUT_MS", 300_000.toString())

        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-all-subnodes")
                .withGenesisNode(node1)
                .withEnv("POSTCHAIN_SUBNODE_IDLE_TIMEOUT_MS", 300_000.toString())

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
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )
        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 10)
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 10)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 10)
                .postTransactionUntilConfirmed("System provider2, provider3 registered, node2, node3 added to the system cluster")

        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(4)
    fun `Deploy Economy Chain`() {
        testLogger.info("Deploying Economy Chain")

        val economyChainGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/economy_chain.xml")!!
                .readText()
                // inject Event Receiver RID
                .replace(
                        "<string>$EIF_EC_EVENT_RECEIVER_BRID_PLACEHOLDER</string>",
                        "<bytea>${BlockchainRid.ZERO_RID.toHex()}</bytea>"
                )
                .replace(
                        "<string>$EIF_EC_STRATEGY_SENDER_BLOCKCHAIN</string>",
                        "<bytea>4EED8C21E3AAB544F172945859E466E55CE3E60180D0C314DB648658CE8DC2A6</bytea>"
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

        val aliceBalance = node1.client(ecBrid, listOf(aliceKeyPair)).getBalance(aliceAuthenticator.accountId)
        testLogger.info("Alice account balance is: $aliceBalance")
        assertThat(aliceBalance).isEqualTo(initialSupply)
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
            makeVoteOnLatestProposal(node3)

            assertThat(getTagByName(APP_CLUSTER_TAG))
                    .isEqualTo(TagData(APP_CLUSTER_TAG, SCU_PRICE, EXTRA_STORAGE_PRICE, EXTRA_COMPUTE_PRICE))
        }
    }

    @Test
    @Order(10)
    fun `Add clusters`() {
        testLogger.info("Adding clusters")

        with(node1.ec) {
            transactionBuilder()
                    .createClusterOperation(node1.providerPubkey, APP_CLUSTER1, "SYSTEM_P", "SYSTEM_P", CONTAINER_UNITS, EXTRA_STORAGE_GIB, APP_CLUSTER_TAG, 50, 2048, 25, 20, 16384, 4, Long.MAX_VALUE)
                    .createClusterOperation(node1.providerPubkey, APP_CLUSTER2, "SYSTEM_P", "SYSTEM_P", CONTAINER_UNITS, EXTRA_STORAGE_GIB, APP_CLUSTER_TAG, 50, 2048, 25, 20, 16384, 4, Long.MAX_VALUE)
                    .postTransactionUntilConfirmed("$APP_CLUSTER1, $APP_CLUSTER2 clusters created")

            // Approve both APP_CLUSTER1 and APP_CLUSTER2
            makeVoteOnLatestProposal(node2)
            makeVoteOnLatestProposal(node3)
            makeVoteOnLatestProposal(node2)
            makeVoteOnLatestProposal(node3)

            awaitQueryResult {
                testLogger.info("Waiting for clusters to be created")

                assertThat(getClusterCreationStatus(APP_CLUSTER1))
                        .isEqualTo(ClusterCreationStatus.SUCCESS)
                assertThat(getClusterCreationStatus(APP_CLUSTER2))
                        .isEqualTo(ClusterCreationStatus.SUCCESS)
            }
            awaitQueryResult {
                val clusters = getClusters()
                assertThat(clusters.any { it.name == APP_CLUSTER1 }).isTrue()
                assertThat(clusters.any { it.name == APP_CLUSTER2 }).isTrue()
            }
        }

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, APP_CLUSTER1)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, APP_CLUSTER1)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, APP_CLUSTER1)
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, APP_CLUSTER2)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, APP_CLUSTER2)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, APP_CLUSTER2)
                .postTransactionUntilConfirmed("$APP_CLUSTER1, $APP_CLUSTER2 clusters created")
        CAC1 = BlockchainRid(node1.c0.cmGetClusterInfo(APP_CLUSTER1).anchoringChain)
        CAC2 = BlockchainRid(node1.c0.cmGetClusterInfo(APP_CLUSTER2).anchoringChain)
    }

    @Test
    @Order(11)
    fun `Create containers`() {
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
        testLogger.info("Deploying dapp to $APP_CLUSTER1")
        deployDapp("test_dapp", containerName)
        dappBrid = dapps["test_dapp"]!!
        awaitQueryResult {
            assertThat(node1.client(dappBrid).currentBlockHeight()).isGreaterThan(0)
        }
    }

    @Test
    @Order(15)
    fun `Upgrade and move container`() {
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
                        containerName, CONTAINER_UNITS + 1, EXTRA_STORAGE_GIB, APP_CLUSTER2, 1, 0)
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

        val newContainerName = containerName + "_m1"
        assertThat(leaseData.containerName).isEqualTo(newContainerName)
        val containerData = node1.c0.getContainerData(leaseData.containerName)
        assertThat(containerData).isNotNull()
        assertThat(containerData.cluster).isEqualTo(APP_CLUSTER2)
        assertThat(containerData.proposedByPubkey).isEqualTo(provider1KeyPair.pubKey.wData)
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

        testLogger.info("Test completed successfully")
    }
}

