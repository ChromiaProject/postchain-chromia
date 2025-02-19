package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_container.createContainerWithUnitsOperation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.model.ContainerResourceLimitType.container_units
import net.postchain.chain0.model.ContainerResourceLimitType.max_blockchains
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_container.proposal_container_limits.proposeContainerLimitsOperation
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.EndpointPool
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.POSTCHAIN_MASTER_PUBKEY
import net.postchain.containers.bpm.resources.Cpu
import net.postchain.containers.bpm.resources.IoRead
import net.postchain.containers.bpm.resources.IoWrite
import net.postchain.containers.bpm.resources.Ram
import net.postchain.containers.bpm.resources.ResourceLimit
import net.postchain.containers.bpm.resources.ResourceLimitFactory
import net.postchain.containers.bpm.resources.Storage
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class Directory1DeploymentBase {

    companion object : ManagedModeBase() {
        val node1Logger = KotlinLogging.logger("Deployment_Node1Logger")
        val node2Logger = KotlinLogging.logger("Deployment_Node2Logger")
        val node3Logger = KotlinLogging.logger("Deployment_Node3Logger")
        override val logsSubdir = "deployment"

        private const val fooContainer = "fooContainer"
        private const val barContainer = "barContainer"
        private val resourceLimitsValues = mapOf("cpu" to 100L, "ram" to 4096L, "io_read" to 50L, "io_write" to 40L)
        private val fooResourceLimits = ContainerResourceLimits(
                Cpu(resourceLimitsValues["cpu"] ?: -1),
                Ram(resourceLimitsValues["ram"] ?: -1),
                Storage(resourceLimitsValues["storage"] ?: 32768),
                IoRead(resourceLimitsValues["io_read"] ?: -1),
                IoWrite(resourceLimitsValues["io_write"] ?: -1)
        )

        @JvmStatic
        @AfterAll
        fun tearDown() {
            super.breakdown()
        }
    }

    abstract val numberOfMasterNodes: Int

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        getDb(node1).awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider1`() {
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
    fun `Add new containers`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assertThat(getSummary().containers).isEqualTo(1L)
            }

            transactionBuilder()
                    .createContainerWithUnitsOperation(
                            node1.providerPubkey, fooContainer, systemCluster, 1,
                            listOf(node1.provider.pubKey.data), 1
                    )
                    .createContainerWithUnitsOperation(
                            node1.providerPubkey, barContainer, systemCluster, 1,
                            listOf(node1.provider.pubKey.data), 1
                    )
                    .postTransactionUntilConfirmed("$fooContainer and $barContainer containers created")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertThat(containers).isEqualTo(setOf(systemContainer, fooContainer, barContainer))
            }
        }
    }

    @Test
    @Order(4)
    fun `Verify and update node and container resource limits`() {
        node1.c0.transactionBuilder()
                .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 2)
                .postTransactionUntilConfirmed("Update node1 to 2 cluster units")

        awaitUntilAsserted {
            val nodeData = node1.c0.getNodeData(node1.pubkey)
            assertThat(nodeData.clusterUnits!!).isEqualTo(2)
        }

        // Asserting that resource limits are defaults
        val expectedLimits = ContainerResourceLimits(Cpu(50L), Ram(2048L), Storage(16384L), IoRead(25), IoWrite(20))
        val actualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertThat(actualLimits).isEqualTo(expectedLimits)

        // Changing resource limits
        node1.c0.transactionBuilder().proposeContainerLimitsOperation(
                node1.providerPubkey,
                fooContainer,
                mapOf(container_units to 2, max_blockchains to 10),
                ""
        ).postTransactionUntilConfirmed("$fooContainer container limits proposed")

        // Asserting resource limits changed
        val newActualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertThat(newActualLimits).isEqualTo(fooResourceLimits)
    }

    @Test
    @Order(5)
    fun `Add node2 as signer to c0`() {
        testLogger.info("Adding node2 to the cluster")
        testLogger.info("Registering provider2")

        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder()
                .registerProviderOperation(node1.providerPubkey, node2.provider.pubKey, ProviderTier.NODE_PROVIDER)
                .proposeProviderIsSystemOperation(node1.providerPubkey, node2.providerPubkey, true, "")
                .postTransactionUntilConfirmed("Register p2 as system")

        node1.client(chain0Brid, listOf(node2.provider)).transactionBuilder()
                .registerNodeWithUnitsOperation(
                        node2.providerPubkey,
                        node2.nodeKeyPair.pubKey.data,
                        node2.nodeHost,
                        node2.nodePort.toLong(),
                        node2.nodeApiPath(),
                        listOf(systemCluster),
                        2
                )
                .postTransactionUntilConfirmed("add node 2 to system cluster")

        // Asserting that node1, node2 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, node1, node2)
        assertChainSigners(clusterAnchoringBrid, node1, node2)
        assertChainSigners(systemAnchoringBrid, node1, node2)
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        testLogger.info("Adding node3 to the cluster")
        testLogger.info("Registering provider3")

        node1.client(chain0Brid, listOf(node1.provider, node2.provider)).transactionBuilder()
                .registerProviderOperation(node1.providerPubkey, node3.provider.pubKey, ProviderTier.NODE_PROVIDER)
                .proposeProviderIsSystemOperation(node1.providerPubkey, node3.providerPubkey, true, "")
                .postTransactionUntilConfirmed("Register p3 as system")

        voteOnAllProposals(listOf(node2.provider))

        testLogger.info("Adding node3 to [node1, node2] network")
        node1.client(chain0Brid, listOf(node3.provider)).transactionBuilder()
                .registerNodeWithUnitsOperation(
                        node3.providerPubkey,
                        node3.pubkey.data,
                        node3.nodeHost,
                        node3.nodePort.toLong(),
                        node3.nodeApiPath(),
                        listOf(systemCluster),
                        2
                )
                .postTransactionUntilConfirmed("add node 3 to system cluster")


        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(7)
    fun `Deploy new dapps`() {
        nodes().forEach { node ->
            assertThat(node.c0.getBlockchains(true).size).isEqualTo(3)
        }

        deployDapp("test_dapp", fooContainer)
        deployDapp("test_dapp2", barContainer, icmfReceiver = dapps["test_dapp"]!!.data)

        // Asserting that blockchain is added
        nodes().forEach { node ->
            assertThat(node.c0.getBlockchains(true).size).isEqualTo(5)
        }
    }

    @Test
    @Order(8)
    fun `Subnode containers have been launched`() {
        testLogger.info("Asserting that subnode container(s) launched")
        awaitUntilAsserted {
            val runningSubnodes = dockerClient.listSubContainersCmd()
                    .withStatusFilter(listOf("running"))
                    .exec()
            assertThat(runningSubnodes.size).isEqualTo(2 * numberOfMasterNodes)
            POSTCHAIN_MASTER_PUBKEY
        }
    }

    @Test
    @Order(9)
    fun `fooContainer has resource limits`() {
        testLogger.info("Asserting $fooContainer resource limits")

        val all = dockerClient.listContainersCmd().withShowAll(true).exec()
        all.forEach {
            if (it.names?.get(0)?.contains(fooContainer) == true) {
                val res = dockerClient.inspectContainerCmd(it.id).exec()
                assertThat(res.hostConfig?.memory).isEqualTo(fooResourceLimits.ramBytes())
                assertThat(res.hostConfig?.cpuQuota).isEqualTo(fooResourceLimits.cpuQuota())
                assertThat(res.hostConfig?.blkioDeviceReadBps?.get(0)?.rate?.toLong()).isEqualTo(fooResourceLimits.ioReadBytes())
                assertThat(res.hostConfig?.blkioDeviceWriteBps?.get(0)?.rate?.toLong()).isEqualTo(fooResourceLimits.ioWriteBytes())
            }
        }
    }

    @Test
    @Order(10)
    fun `Transactions can be sent to test_dapp`() {
        assertThatDappProcessesTx(dapps["test_dapp"]!!, "add_city", "Heraklion", "get_cities")
    }

    @Test
    @Order(11)
    fun `Transactions can be sent to test_dapp2`() {
        assertThatDappProcessesTx(dapps["test_dapp2"]!!, "add_book", "Mastering Bitcoin", "get_books")
    }

    @Test
    @Order(12)
    fun `Blocks can be anchored`() {
        assertThatBlocksAreAnchored(clusterAnchoringBrid, dapps["test_dapp"]!!)
        assertThatBlocksAreAnchored(clusterAnchoringBrid, dapps["test_dapp2"]!!)
    }

    @Test
    @Order(13)
    fun `Cluster anchoring chain blocks are anchored in system anchoring chain`() {
        assertThatBlocksAreAnchored(systemAnchoringBrid, clusterAnchoringBrid)
    }

    private fun assertThatBlocksAreAnchored(anchoringChainBrid: BlockchainRid, sourceBrid: BlockchainRid) {
        awaitUntilAsserted {
            nodes().forEach { node ->
                val lastAnchoredBlock = awaitQueryResult {
                    node.client(anchoringChainBrid).getLastAnchoredBlock(sourceBrid)
                }
                assertThat(lastAnchoredBlock).isNotNull()

                val sourceChainBlock = awaitQueryResult {
                    node.client(sourceBrid).blockAtHeight(lastAnchoredBlock!!.blockHeight)
                }
                assertThat(sourceChainBlock).isNotNull()

                assertThat(sourceChainBlock!!.rid).isEqualTo(lastAnchoredBlock!!.blockRid)

                val sourceWitness = BaseBlockWitness.fromBytes(sourceChainBlock.witness.data)
                val anchorWitness = BaseBlockWitness.fromBytes(lastAnchoredBlock.witness.data)

                assertThat(sourceWitness.getSignatures().size).isEqualTo(anchorWitness.getSignatures().size)
                sourceWitness.getSignatures().forEach { sourceSignature ->
                    assertThat(anchorWitness.getSignatures().any {
                        it.subjectID.contentEquals(sourceSignature.subjectID) && it.data.contentEquals(sourceSignature.data)
                    }).isTrue()
                }
            }
        }
    }

    @Test
    @Order(14)
    fun `ICMF messages are delivered`() {
        val receiverDapp = dapps["test_dapp2"]!!
        assertDappQuery(receiverDapp, "get_icmf_cities", "Heraklion")
    }

    @Test
    @Order(15)
    fun `ICCF transfers are validated`() {
        val chromiaClientProvider = ChromiaClientProvider(
                ContainerClusterManagement(
                        ClusterManagementImpl(node1.c0), mapOf(systemCluster to listOf(node1.peerInfo(), node2.peerInfo(), node3.peerInfo()))
                ),
                PostchainClientConfig(BlockchainRid.ZERO_RID, EndpointPool.singleUrl(""), merkleHashVersion = 1) // TODO [use-new-algo] use new hash version
        )
        verifyICCF(chromiaClientProvider)
    }

    private fun queryContainerResourceLimits(): Array<ResourceLimit> {
        return node1.c0.nmGetContainerLimits(fooContainer)
                .mapNotNull {
                    ResourceLimitFactory.fromPair(it.toPair())
                }.toTypedArray()
    }

    @Test
    @Order(16)
    fun `Reconfiguration of test_dapp2`() {
        val icmfReceiver = dapps["test_dapp"]!!.data
        val dapp2brid = dapps["test_dapp2"]!!

        // initial value 500
        nodes().forEach {
            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, dapp2brid)).isEqualTo(setOf(500))
        }

        // reconfiguring test_dapp2
        updateDapp("test_dapp2", maxBlockTransactions = 17100, faulty = false, icmfReceiver)
        updateDapp("test_dapp2", maxBlockTransactions = 17200, faulty = true, icmfReceiver)
        updateDapp("test_dapp2", maxBlockTransactions = 17300, faulty = false, icmfReceiver)

        // new values: 17100, 17300
        awaitUntilAsserted {
            nodes().forEach {
                assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, dapp2brid)).isEqualTo(setOf(500, 17100, 17300))
            }
        }
    }

    @Test
    @Order(17)
    fun `Test blockchain state changes`() {
        // Verify state is RUNNING
        nodes().forEach {
            verifyBlockchainState(it, chain0Brid, BlockchainState.RUNNING)
            verifyBlockchainState(it, systemAnchoringBrid, BlockchainState.RUNNING)
            verifyBlockchainState(it, clusterAnchoringBrid, BlockchainState.RUNNING)
        }
        val dappBrid = dapps["test_dapp"]!!
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)
        awaitUntilAsserted {
            assertThat(node1.tx(dappBrid, "do_nothing", gtv(1)).second.httpStatusCode!!).isEqualTo(200)
        }

        // Change to PAUSED
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("Change state to ${BlockchainState.PAUSED.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.PAUSED)

        // Verify no transactions created but chain is reachable
        awaitUntilAsserted {
            assertThat(node1.tx(dappBrid, "do_nothing", gtv(2)).second.httpStatusCode!!).isEqualTo(403)
        }
        assertDappQuery(dappBrid, "get_cities", "Heraklion")

        // Change to RUNNING
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.resume, "")
                .postTransactionUntilConfirmed("Change state to ${BlockchainState.RUNNING.name} for dapp $dappBrid")
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)

        // Verify transactions created
        awaitUntilAsserted {
            assertThat(node1.tx(dappBrid, "do_nothing", gtv(3)).second.httpStatusCode!!).isEqualTo(200)
        }
    }

    @Test
    @Order(18)
    fun `Test remove and archive blockchains`() {
        // Deploy a new dapps to prevent `fooContainer` from stopping when `test_dapp` is REMOVED
        deployDapp("test_dapp3", fooContainer)
        deployDapp("test_dapp5", fooContainer)
        nodes().forEach { node ->
            assertThat(node.c0.getBlockchains(true).size).isEqualTo(7)
        }

        // Verify state is RUNNING
        val dappBrid = dapps["test_dapp"]!!
        val dapp3Brid = dapps["test_dapp3"]!!
        val dapp5Brid = dapps["test_dapp5"]!!
        val chainId = getDb(node1).getChainId(dappBrid)
        val chain3Id = getDb(node1).getChainId(dapp3Brid)
        verifyBlockchainState(node1, dappBrid, BlockchainState.RUNNING)
        verifyBlockchainState(node1, dapp3Brid, BlockchainState.RUNNING)
        verifyBlockchainState(node1, dapp5Brid, BlockchainState.RUNNING)

        // 1. Removing test_dapp, archiving test_dapp3
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.remove, "")
                .proposeBlockchainActionOperation(node1.providerPubkey, dapp3Brid, BlockchainAction.archive, "")
                .postTransactionUntilConfirmed("Removing test_dapp and archiving test_dapp3")

        // Verify state is REMOVED and ARCHIVED
        verifyBlockchainState(node1, dappBrid, BlockchainState.REMOVED)
        verifyBlockchainState(node1, dapp3Brid, BlockchainState.ARCHIVED)

        // Verify that removed chains are deleted from DB
        awaitUntilAsserted {
            assertThat(getDb(node1).getChainId(dappBrid)).isNull()
            assertThat(getDb(node2).getChainId(dappBrid)).isNull()
            assertThat(getDb(node3).getChainId(dappBrid)).isNull()
        }

        // Verify that removed chain is deleted from subnode DBs
        if (numberOfMasterNodes > 0) {
            val fooDockerContainer = dockerClient.listContainersCmd().withShowAll(true).exec().firstOrNull {
                it.names.any { name -> name.contains(fooContainer) }
            }
            assertThat(fooDockerContainer).isNotNull()

            Awaitility.await().pollInterval(Duration.FIVE_SECONDS).atMost(Duration.TWO_MINUTES).untilAsserted {
                val logs = getContainerLogs(dockerClient, fooDockerContainer!!)
                assertThat(logs).contains("chain-id=$chainId]: Deleting blockchain")
                assertThat(logs).contains("chain-id=$chainId]: Blockchain deleted in")
                assertThat(logs).contains("chain-id=$chain3Id]: Archiving blockchain")
                assertThat(logs).contains("chain-id=$chain3Id]: Blockchain archived in")
            }
        }

        // 2. Removing already archived blockchain test_dapp3
        node1.c0.transactionBuilder()
                .proposeBlockchainActionOperation(node1.providerPubkey, dapp3Brid, BlockchainAction.remove, "")
                .postTransactionUntilConfirmed("Removing test_dapp3")

        // Verify state is REMOVED
        verifyBlockchainState(node1, dapp3Brid, BlockchainState.REMOVED)

        // Verify that removed chains are deleted from DB
        awaitUntilAsserted {
            assertThat(getDb(node1).getChainId(dapp3Brid)).isNull()
            assertThat(getDb(node2).getChainId(dapp3Brid)).isNull()
            assertThat(getDb(node3).getChainId(dapp3Brid)).isNull()
        }

        // Verify that removed chain is deleted from subnode DBs
        if (numberOfMasterNodes > 0) {
            val fooDockerContainer = dockerClient.listContainersCmd().withShowAll(true).exec().firstOrNull {
                it.names.any { name -> name.contains(fooContainer) }
            }
            assertThat(fooDockerContainer).isNotNull()

            Awaitility.await().pollInterval(Duration.FIVE_SECONDS).atMost(Duration.TWO_MINUTES).untilAsserted {
                val logs = getContainerLogs(dockerClient, fooDockerContainer!!)
                assertThat(logs).contains("chain-id=$chain3Id]: Deleting blockchain")
                assertThat(logs).contains("chain-id=$chain3Id]: Blockchain deleted in")
            }
        }
    }

    @Test
    @Order(19)
    fun `Subnode container stops if empty`() {
        if (numberOfMasterNodes > 0) {
            testLogger.info("Asserting that the container stops if there are no running blockchains in it")

            // Removing test_dapp5
            node1.c0.transactionBuilder()
                    .proposeBlockchainActionOperation(node1.providerPubkey, dapps["test_dapp5"]!!, BlockchainAction.remove, "")
                    .postTransactionUntilConfirmed("Removing test_dapp5")

            awaitUntilAsserted {
                val fooDockerContainer = dockerClient.listContainersCmd().withShowAll(true).exec().firstOrNull {
                    it.names.any { name -> name.contains(fooContainer) }
                }
                assertThat(fooDockerContainer?.state).isEqualTo("exited")
            }
        }
    }
}
