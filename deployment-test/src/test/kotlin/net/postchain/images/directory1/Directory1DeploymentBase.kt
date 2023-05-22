package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.legacy_anchoring.integrated.getLastLegacyAnchoredBlock
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_container.proposal_container_limits.proposeContainerLimitsOperation
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.TxRid
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.resources.*
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
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
import org.mandas.docker.client.DockerClient
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class Directory1DeploymentBase {

    companion object : ManagedModeBase() {
        val node1Logger = KotlinLogging.logger("Deployment_Node1Logger")
        val node2Logger = KotlinLogging.logger("Deployment_Node2Logger")
        val node3Logger = KotlinLogging.logger("Deployment_Node3Logger")

        private const val foobarContainer = "foobar"
        private val resourceLimitsValues = mapOf("cpu" to 50L, "ram" to 2048L, "io_read" to 50L, "io_write" to 50L)
        private val foobarResourceLimits = ContainerResourceLimits(
                Cpu(resourceLimitsValues["cpu"] ?: -1),
                Ram(resourceLimitsValues["ram"] ?: -1),
                Storage(resourceLimitsValues["storage"] ?: -1),
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
        node1Db.awaitBlockHeight(0)
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
    fun `Add new container`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assertThat(getSummary().containers).isEqualTo(1L)
            }

            transactionBuilder()
                    .createContainerOperation(
                            node1.providerPubkey,
                            foobarContainer,
                            systemCluster,
                            1,
                            listOf(node1.provider.pubKey.data)
                    )
                    .postTransactionUntilConfirmed("$foobarContainer container")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertEquals(setOf(systemContainer, foobarContainer), containers)
            }
        }
    }

    @Test
    @Order(4)
    fun `Add container resource limits`() {
        // Asserting that resource limits are defaults
        val expectedLimits = ContainerResourceLimits(Cpu(-1L), Ram(-1L), Storage(-1L), IoRead(-1), IoWrite(-1))
        val actualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertEquals(expectedLimits, actualLimits)

        // Changing resource limits
        with(resourceLimitsValues) {
            node1.c0.transactionBuilder().proposeContainerLimitsOperation(
                    node1.providerPubkey,
                    foobarContainer,
                    mapOf(
                            cpu to getOrDefault("cpu", -1),
                            ram to getOrDefault("ram", -1),
                            storage to getOrDefault("storage", -1),
                            io_read to getOrDefault("io_read", -1),
                            io_write to getOrDefault("io_write", -1)
                    ),
                    ""
            ).postTransactionUntilConfirmed("container limits")
        }

        // Asserting resource limits changed
        val newActualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertEquals(foobarResourceLimits, newActualLimits)
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
                .registerNodeOperation(
                        node2.providerPubkey,
                        node2.nodeKeyPair.pubKey.data,
                        node2.nodeHost,
                        node2.nodePort.toLong(),
                        node2.nodeApiPath(),
                        listOf(systemCluster)
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

        voteOnAllProposals(node2.provider)

        testLogger.info("Adding node3 to [node1, node2] network")
        node1.client(chain0Brid, listOf(node3.provider)).transactionBuilder()
                .registerNodeOperation(
                        node3.providerPubkey,
                        node3.pubkey.data,
                        node3.nodeHost,
                        node3.nodePort.toLong(),
                        node3.nodeApiPath(),
                        listOf(systemCluster)
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

        deployDapp("test_dapp", systemContainer, null)
        deployDapp("test_dapp2", foobarContainer, dapps["test_dapp"]!!.data)

        // Asserting that blockchain is added
        nodes().forEach { node ->
            assertThat(node.c0.getBlockchains(true).size).isEqualTo(5)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        testLogger.info("Asserting that subnode container(s) launched")
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            val runningSubnodes = all.filter { it.image().contains("chromia-subnode") && it.state() == "running" }
            assertThat(runningSubnodes.size).isEqualTo(2 * numberOfMasterNodes)
        }
    }

    @Test
    @Order(9)
    fun `Subnode container has resource limits`() {
        testLogger.info("Asserting container resource limits")

        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        all.forEach {
            if (it.names()?.get(0)?.contains(foobarContainer) == true) {
                val res = dockerClient.inspectContainer(it.id())
                assertEquals(foobarResourceLimits.ramBytes(), res.hostConfig()?.memory())
                assertEquals(foobarResourceLimits.cpuQuota(), res.hostConfig()?.cpuQuota())
                assertEquals(foobarResourceLimits.ioReadBytes(), res.hostConfig().blkioDeviceReadBps()[0].rate().toLong())
                assertEquals(foobarResourceLimits.ioWriteBytes(), res.hostConfig().blkioDeviceWriteBps()[0].rate().toLong())
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

    private fun assertThatDappProcessesTx(brid: BlockchainRid, txOp: String, txArg: String, query: String) {
        testLogger.info("Send TX to new dapp ${brid.toHex()} and fetch data")
        dappTxs[brid] = node2.tx(brid, txOp, gtv(txArg)).first
        awaitUntilAsserted {
            nodes().forEach { node ->
                val cities = awaitQueryResult { node.client(brid).query(query, gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assertThat(cities).containsExactly(txArg)
            }
        }
    }

    //    @Disabled
//    @Test
//    @Order(12)
    fun `Legacy anchoring can anchor blocks`() {
        assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dapps["test_dapp"]!!)
        assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dapps["test_dapp2"]!!)
    }

    private fun assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dappBrid: BlockchainRid) {
        awaitUntilAsserted {
            nodes().forEach { node ->
                val lastAnchoredBlock = awaitQueryResult {
                    node.c0.getLastLegacyAnchoredBlock(dappBrid)
                }
                assertThat(lastAnchoredBlock).isNotNull()

                val dappChainBlock = awaitQueryResult {
                    node.client(dappBrid).blockAtHeight(lastAnchoredBlock!!.height)
                }
                assertThat(dappChainBlock).isNotNull()

                assertThat(dappChainBlock!!.rid).isEqualTo(lastAnchoredBlock!!.blockRid)
            }
        }
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
        awaitUntilAsserted {
            nodes().forEach { node ->
                val cities = awaitQueryResult { node.client(receiverDapp).query("get_icmf_cities", gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assertThat(cities).containsExactly("Heraklion")
            }
        }
    }

    @Test
    @Order(15)
    fun `ICCF transfers are validated`() {
        val sourceDapp = dapps["test_dapp"]!!
        val targetDapp = dapps["test_dapp2"]!!

        val txToProve = dappTxs[sourceDapp]!!
        val chromiaClientProvider = ChromiaClientProvider(FailOverConfig(),
                ContainerClusterManagement(
                        ClusterManagementImpl(node1.c0), listOf(node1.peerInfo(), node2.peerInfo(), node3.peerInfo())))
        val iccfMaterial = IccfProofTxMaterialBuilder(chromiaClientProvider).build(
                TxRid(txToProve.gtxBody.rid.toHex()),
                txToProve.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem)),
                listOf(),
                sourceDapp,
                targetDapp
        )
        val actualTxToProve = iccfMaterial.updatedTx ?: txToProve
        iccfMaterial.txBuilder.addOperation("iccf_transfer", actualTxToProve.toGtv())
                .postTransactionUntilConfirmed("iccf_transfer")
        awaitUntilAsserted {
            nodes().forEach { node ->
                val cities = awaitQueryResult { node.client(targetDapp).query("get_iccf_cities", gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assertThat(cities).containsExactly("Heraklion")
            }
        }
    }

    private fun queryContainerResourceLimits(): Array<ResourceLimit> {
        return node1.c0.nmGetContainerLimits(foobarContainer)
                .mapNotNull {
                    ResourceLimitFactory.fromPair(it.toPair())
                }.toTypedArray()
    }

    @Test
    @Order(16)
    fun `Reconfiguration of test_dapp2`() {
        val iccfReceiver = dapps["test_dapp"]!!.data
        val dapp2brid = dapps["test_dapp2"]!!

        // initial value 500
        nodes().forEach {
            assertEquals(setOf(500), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, dapp2brid))
        }

        // reconfiguring test_dapp2
        updateDapp("test_dapp2", maxBlockTransactions = 17100, faulty = false, iccfReceiver)
        updateDapp("test_dapp2", maxBlockTransactions = 17200, faulty = true, iccfReceiver)
        updateDapp("test_dapp2", maxBlockTransactions = 17300, faulty = false, iccfReceiver)

        // new values: 17100, 17300
        awaitUntilAsserted {
            nodes().forEach {
                assertEquals(setOf(500, 17100, 17300), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, dapp2brid))
            }
        }
    }

    private fun deployDapp(dappName: String, containerName: String, iccfReceiver: ByteArray?) {
        testLogger.info("Deploy new dapp $dappName")

        val configGtv = compileDapp(dappName, iccfReceiver = iccfReceiver)

        node3Db.awaitNewBlock()

        val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configGtv, cryptoSystem)
        dapps[dappName] = blockchainRid
        testLogger.info { "Proposing a blockchain ${blockchainRid.toHex()} with config" }

        node1.c0.transactionBuilder()
                .proposeBlockchainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(configGtv), "dapp", containerName, "")
                .postTransactionUntilConfirmed("Propose dapp $blockchainRid")

        if (containerName == systemContainer) {
            voteOnAllProposals(node2.provider)
            voteOnAllProposals(node3.provider)
        }

        // Asserting that node1, node2, node3 are signers of newly added blockchain
        assertChainSigners(blockchainRid, *nodes())
    }

    private fun updateDapp(dappName: String, maxBlockTransactions: Int, faulty: Boolean, iccfReceiver: ByteArray) {
        testLogger.info("Update dapp $dappName")

        val configGtv = compileDapp(dappName, maxBlockTransactions, iccfReceiver, faulty)

        node1.c0.transactionBuilder()
                .proposeConfigurationOperation(node1.providerPubkey, dapps[dappName]!!, GtvEncoder.encodeGtv(configGtv), "")
                .postTransactionUntilConfirmed("Propose $dappName config")
    }
}
