package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetPeerInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.chain0.common.operations.registerNodeOperation
import net.postchain.chain0.common.operations.registerProviderOperation
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.legacy_anchoring.integrated.getLastLegacyAnchoredBlock
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetBlockchainConfigurationV5
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.chain0.proposal.getProposalsSince
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal.voting.makeVoteOperation
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_cluster.proposeClusterProviderOperation
import net.postchain.chain0.proposal_container.proposal_container_limits.proposeContainerLimitsOperation
import net.postchain.chain0.proposal_provider.proposeProviderIsSystemOperation
import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.TxRid
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.containers.bpm.resources.*
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.Gtx
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.AfterAll
import org.junitpioneer.jupiter.DisableIfTestFails
import org.mandas.docker.client.DockerClient
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.lang.ProcessBuilder.Redirect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class Directory1DeploymentBase {

    companion object : ManagedModeBase() {
        @JvmStatic
        protected val resolvedDockerHost = getResolvedDockerHost()
        private val dockerClient: DockerClient = DockerClientFactory.create()
        private val dapps = mutableMapOf<String, BlockchainRid>()
        lateinit var clusterAnchoringBrid: BlockchainRid
        lateinit var systemAnchoringBrid: BlockchainRid
        private val dappTxs = mutableMapOf<BlockchainRid, Gtx>()
        private const val systemContainer = "system"
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
        fun breakdown() {
            saveSubnodeLogs(dockerClient)
            stopNodes()
            removeSubnodeContainers()

            /*
                This is used by the CI to run a shell command right before the
                files in the directory referenced by MOUNT_DIR are removed. It
                is necessary because the permissions need to be altered, since
                the files are owned by the root user account.
            */
            val testBreakdownCommand = System.getenv("TEST_BREAKDOWN_COMMAND")

            if (testBreakdownCommand != null) {
                ProcessBuilder(testBreakdownCommand)
                        .redirectOutput(Redirect.INHERIT)
                        .redirectError(Redirect.INHERIT)
                        .start()
                        .waitFor()
            }

            if (!File(PostchainContainer.MOUNT_DIR).deleteRecursively()) {
                testLogger.error("Unable to clear mount directory")
            }
        }

        fun removeSubnodeContainers() {
            dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).forEach {
                if (it.image().contains("chromia-subnode")) {
                    dockerClient.stopContainer(it.id(), 0)
                    dockerClient.removeContainer(it.id())
                }
            }
        }
    }

    private val cryptoSystem = Secp256K1CryptoSystem()

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

    private fun assertAnchoringChainProperties() {
        val systemChains = node1.c0.nmComputeBlockchainInfoList(node1.nodeKeyPair.pubKey.data)
                .filter { it.system }.map { BlockchainRid(it.rid) }
        assertEquals(3, systemChains.size)

        // Getting cluster anchoring chain for system cluster via CM API
        clusterAnchoringBrid = BlockchainRid(node1.c0.cmGetClusterInfo("system").anchoringChain)
        // Asserting cluster anchoring chain is in system_chains list of NP API
        assertThat(systemChains.map { it }).contains(clusterAnchoringBrid)
        testLogger.info("Cluster anchor chain bc-rid: $clusterAnchoringBrid")

        systemAnchoringBrid = BlockchainRid(node1.c0.cmGetSystemAnchoringChain()!!)
        assertThat(systemChains.map { it }).contains(systemAnchoringBrid)
        testLogger.info("System anchor chain bc-rid: $systemAnchoringBrid")
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
                            "system",
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
                        listOf("system")
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
                        listOf("system")
                )
                .postTransactionUntilConfirmed("add node 3 to system cluster")


        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    private fun voteOnAllProposals(provider: KeyPair) {
        val proposals = awaitQueryResult {
            val result = node1.c0.getProposalsSince(RowId(0))
            assertThat(result).isNotEmpty()
            return@awaitQueryResult result
        }!!

        proposals.sortedBy { it.rowid.id }.forEach {
            node1.client(chain0Brid, listOf(provider)).transactionBuilder()
                    .makeVoteOperation(provider.pubKey.data, it.rowid.id, true)
                    .postTransactionUntilConfirmed("provider ${provider.pubKey.hex()} vote on ${it.rowid}, ${it.proposalType}")
        }
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
    @Order(13)
    fun `Blocks can be anchored`() {
        assertThatBlocksAreAnchored(clusterAnchoringBrid, dapps["test_dapp"]!!)
        assertThatBlocksAreAnchored(clusterAnchoringBrid, dapps["test_dapp2"]!!)
    }

    @Test
    @Order(14)
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
    @Order(15)
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
    @Order(16)
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

    private fun assertChainSigners(blockchainRid: BlockchainRid, vararg nodes: PostchainContainer) {
        awaitQueryResult {
            val currentHeight = node1.client(blockchainRid).currentBlockHeight()
            val actual = node1.c0.cmGetPeerInfo(blockchainRid.data, currentHeight).map { PubKey(it) }.toSet()
            val expected = nodes.map { it.pubkey }.toSet()
            assertEquals(expected, actual)
        }
    }

    @Test
    @Order(17)
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

    @Test
    @Order(18)
    fun `Reconfiguration cluster anchoring chain`() {
        testLogger.info("Update cluster anchoring chain")

        // initial value 500
        nodes().forEach {
            assertEquals(setOf(500), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, clusterAnchoringBrid))
        }

        listOf(18200, 18300, 18400).forEach {
            node1.c0.transactionBuilder()
                    .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid,
                            GtvEncoder.encodeGtv(compileDapp("cluster_anchoring", maxBlockTransactions = it, faulty = it == 18300)), "")
                    .postTransactionUntilConfirmed("Propose cluster anchoring chain config")
            voteOnAllProposals(node2.provider)
            voteOnAllProposals(node3.provider)
        }

        // new values: 18200, 18400
        awaitUntilAsserted {
            nodes().forEach {
                assertEquals(setOf(500, 18200, 18400), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, clusterAnchoringBrid))
            }
        }
    }

    @Test
    @Order(19)
    fun `Reconfiguration system anchoring chain`() {
        testLogger.info("Update $systemAnchoringBrid")

        // initial value 500
        nodes().forEach {
            assertEquals(setOf(500), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, systemAnchoringBrid))
        }

        listOf(19500, 19600, 19700).forEach {
            node1.c0.transactionBuilder()
                    .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid,
                            GtvEncoder.encodeGtv(compileDapp("system_anchoring", maxBlockTransactions = it, faulty = it == 19600)), "")
                    .postTransactionUntilConfirmed("Propose $systemAnchoringBrid config")
            voteOnAllProposals(node2.provider)
            voteOnAllProposals(node3.provider)
        }

        // new values: 19500, 19700
        awaitUntilAsserted {
            nodes().forEach {
                assertEquals(setOf(500, 19500, 19700), getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(it, systemAnchoringBrid))
            }
        }
    }

    @Test
    @Order(20)
    fun `Add new cluster and reconfigure CAC by updates of different types in single tx`() {
        testLogger.info("Creating pcu_cluster [node1]")
        val pcuVs = "pcu_vs"
        val pcuCluster = "pcu_cluster"

        // 1. Create a new cluster `pcu_cluster`
        node1.c0.transactionBuilder()
                .createVoterSetOperation(node1.providerPubkey, pcuVs, 1, listOf(node1.providerPubkey), null)
                .postTransactionUntilConfirmed("Create voterset: $pcuVs")
        awaitQueryResult {
            assertTrue(node1.c0.getVoterSets().any { it.name == pcuVs })
        }

        node1.c0.transactionBuilder()
                .createClusterOperation(node1.providerPubkey, pcuCluster, pcuVs, listOf(node1.providerPubkey))
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, pcuCluster)
                .postTransactionUntilConfirmed("Create cluster $pcuCluster with provider1/node1")

        awaitQueryResult {
            assertTrue(node1.c0.getClusters().any { it.name == pcuCluster })
            val chains = node1.c0.getClusterBlockchains(pcuCluster)
            assertEquals(1, chains.size)
            val brid = BlockchainRid(chains.first())

            // signers from cluster anchoring chain (CAC) config
            val actual = getLastBlockConfigSigners(node1, brid)
            assertEquals(setOf(node1.pubkey.wData), actual.toSet())
        }

        // 2. Mixed tx: proposing config, signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for pcu_cluster's CAC: config, signer, faulty config, config again")
        val cac = BlockchainRid(node1.c0.cmGetClusterInfo(pcuCluster).anchoringChain)
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose good config
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20100), "")
                // add node2
                .proposeClusterProviderOperation(node1.providerPubkey, pcuCluster, node2.providerPubkey, true, "")
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, pcuCluster)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20200, true), "")
                // add node3
                .proposeClusterProviderOperation(node1.providerPubkey, pcuCluster, node3.providerPubkey, true, "")
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, pcuCluster)
                // propose good config
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20300), "")
                .postTransactionUntilConfirmed("Propose pcu_cluster anchoring chain configs")

        // new values: 20100, 20300
        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData, node3.pubkey.wData),
                    getLastBlockConfigSigners(node1, cac).toSet())

            assertEquals(
                    setOf(500, 20100, 20300),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, cac))
        }

        // 3. Mixed tx: proposing config, removing signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for pcu_cluster's CAC: config, removing signer, faulty config, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node3.provider))
                // Setting 1000 again, as it was before node3 was added,
                // to propose config which matches with one of the already applied ones.
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20100), "")
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20500, true), "")
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20600), "")
                .postTransactionUntilConfirmed("Propose pcu_cluster anchoring chain configs")

        // new values added: 20600
        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, cac).toSet())

            assertEquals(
                    setOf(500, 20100, 20300, 20600),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, cac))
        }

        // 4. Proposing a faulty signers config
        /* TODO: [POS-804]: Fix it.
        testLogger.info("Proposing faulty pending config with removed signer")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider))
                // proposing faulty pending_config1 and pending_removed_signers_config2,
                // so that config2 will contain base_config1 and will fail
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20700, true), "")
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .proposeConfigurationOperation(node1.providerPubkey, cac, buildConfig(20800), "")
                .postTransactionUntilConfirmed("Propose pcu_cluster anchoring chain configs")
        // new values added: 20800
        awaitQueryResult {
            assertEquals(
                    setOf(node1.pubkey.wData, node2.pubkey.wData),
                    getLastBlockConfigSigners(node1, cac).toSet())

            assertEquals(
                    setOf(500, 20100, 20300, 20600, 20800),
                    getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, cac))
        }
         */
    }

    private fun buildConfig(param: Int, faulty: Boolean = false) = GtvEncoder.encodeGtv(compileDapp("cluster_anchoring", param, faulty = faulty))

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

    private fun compileDapp(dappName: String, maxBlockTransactions: Int = 500, iccfReceiver: ByteArray? = null, faulty: Boolean = false): Gtv =
            GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/$dappName.xml")!!.readText()
                    .replace("<int>500</int>", "<int>$maxBlockTransactions</int>")
                    .let {
                        if (iccfReceiver != null) it.replace("<string>DAPP_BRID</string>", "<bytea>${iccfReceiver.toHex()}</bytea>") else it
                    }
                    .let {
                        if (faulty) it.replace("<string>net.postchain.gtx.StandardOpsGTXModule</string>",
                                "<string>net.postchain.gtx.StandardOpsGTXModule</string>\n<string>unknown_module</string>") else it
                    })

    private fun getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node: PostchainContainer, blockchainRid: BlockchainRid): Set<Int> {
        val res = mutableSetOf<Int>()
        var current: Long? = 0L

        while (current != null) {
            val config = node.c0.nmGetBlockchainConfiguration(blockchainRid, current) ?: break
            res.add(GtvDecoder.decodeGtv(config).asDict()["blockstrategy"]!!["maxblocktransactions"]!!.asInteger().toInt())
            current = node.c0.nmFindNextConfigurationHeight(blockchainRid, current)
        }

        return res
    }

    private fun getLastBlockConfigSigners(node: PostchainContainer, blockchainRid: BlockchainRid): List<WrappedByteArray> {
        val lastHeight = node1.client(blockchainRid).currentBlockHeight()
        return node.c0.nmGetBlockchainConfigurationV5(blockchainRid, lastHeight)!!.signers
    }

    private val PostchainContainer.c0 get() = client(chain0Brid)

    private val PostchainContainer.providerPubkey get() = provider.pubKey.data
}
