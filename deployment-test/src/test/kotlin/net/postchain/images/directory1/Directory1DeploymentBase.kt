package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.common.registerNodeOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.common.updateNodeOperation
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.chain0.container.container_op.createContainerOperation
import net.postchain.chain0.legacy_anchoring.integrated.getLastLegacyAnchoredBlock
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.TxRid
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.common.types.RowId
import net.postchain.common.wrap
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.containers.bpm.resources.*
import net.postchain.crypto.KeyPair
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.Gtx
import net.postchain.images.common.ManagedModeBase
import net.postchain.mc.cli.base.cryptoSystem
import net.postchain.rell.tools.runcfg.RellPostAppChainConfig
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.junitpioneer.jupiter.DisableIfTestFails
import org.mandas.docker.client.DockerClient
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val systemRellSource = "../chain0-impl/rell/src"

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class Directory1DeploymentBase {

    companion object : ManagedModeBase(systemRellSource) {
        @JvmStatic
        protected val resolvedDockerHost = getResolvedDockerHost()
        private val dockerClient: DockerClient = DockerClientFactory.create()
        private val dapps = mutableMapOf<String, BlockchainRid>()
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
            val clusterAnchoringDapp = compileChain("anchoring/blockchain_config_cluster_anchoring.run.xml", File(systemRellSource))
            val clusterAnchoringGtvConfig = getBaseConfig(clusterAnchoringDapp.config.chains.first().configs.entries.first().value)

            // compile system anchoring dapp
            val systemAnchoringDapp = compileChain("anchoring/blockchain_config_system_anchoring.run.xml", File(systemRellSource))
            val systemAnchoringGtvConfig = getBaseConfig(systemAnchoringDapp.config.chains.first().configs.entries.first().value)

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")

            assert(getSummary().providers).isEqualTo(1L)
            assert(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }

        assertAnchoringChainProperties()

        // This will replace the dummy URL {apiUrl} in config
        node1.c0.transactionBuilder()
                .updateNodeOperation(node1.providerPubkey, node1.pubkey.data, null, null, node1.nodeApiPath())
                .postTransactionUntilConfirmed("Fix node1 REST API URL")
    }

    private fun assertAnchoringChainProperties() {
        val systemChains = node1.c0.nmComputeBlockchainInfoList(node1.nodeKeyPair.pubKey.data).filter { it.system }
        assertEquals(3, systemChains.size)

        // Getting cluster anchoring chain for system cluster via CM API
        val clusterAnchoringChainBrid = node1.c0.cmGetClusterInfo("system").anchoringChain
        // Asserting cluster anchoring chain is in system_chains list of NP API
        assert(systemChains.map { it.rid }).contains(clusterAnchoringChainBrid)
        testLogger.info("Cluster anchor chain bc-rid: ${clusterAnchoringChainBrid.toHex()}")

        val systemAnchoringChainBrid = node1.c0.cmGetSystemAnchoringChain()!!.wrap()
        assert(systemChains.map { it.rid }).contains(systemAnchoringChainBrid)
        testLogger.info("System anchor chain bc-rid: ${systemAnchoringChainBrid.toHex()}")
    }

    @Test
    @Order(3)
    fun `Add new container`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assert(getSummary().containers).isEqualTo(1L)
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
        // Asserting that node2 is signers of chain0
        awaitQueryResult {
            assert(node1.c0.getBlockchainSigners(chain0Brid).size).isEqualTo(2)
            assert(node2.c0.getBlockchainSigners(chain0Brid).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        testLogger.info("Adding node3 to the cluster")
        testLogger.info("Registering provider3")
        node1Db.awaitNewBlock()
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

        // Asserting that node2 is signers of chain0
        awaitQueryResult {
            assert(node1.c0.getBlockchainSigners(chain0Brid).size).isEqualTo(3)
            assert(node2.c0.getBlockchainSigners(chain0Brid).size).isEqualTo(3)
            assert(node3.c0.getBlockchainSigners(chain0Brid).size).isEqualTo(3)
        }
    }

    private fun voteOnAllProposals(provider: KeyPair) {
        awaitQueryResult {
            assert(node1.c0.getProposalsSince(RowId(0))).isNotEmpty()
        }

        node1.c0.getProposalsSince(RowId(0)).sortedBy { it.rowid.id }.forEach {
            node1.client(chain0Brid, listOf(provider)).transactionBuilder()
                    .makeVoteOperation(provider.pubKey.data, it.rowid.id, true)
                    .postTransactionUntilConfirmed("provider ${provider.pubKey.hex()} vote on ${it.rowid}, ${it.proposalType}")
        }
    }

    @Test
    @Order(7)
    fun `Deploy new dapp`(@TempDir tmpIcmfSources: File, @TempDir tmpIccfSources: File) {
        listOf(node1, node2, node3).forEach { node ->
            assert(node.c0.getBlockchains(true).size).isEqualTo(3)
        }

        File("../chain0-impl/rell/src/icmf").copyRecursively(tmpIcmfSources.resolve("icmf"))
        deployDapp("test-dapp", systemContainer, tmpIcmfSources)
        File("../chain0-impl/rell/src/iccf").copyRecursively(tmpIccfSources.resolve("iccf"))
        deployDapp("test-dapp2", foobarContainer, tmpIccfSources)

        // Asserting that blockchain is added
        listOf(node1, node2, node3).forEach { node ->
            assert(node.c0.getBlockchains(true).size).isEqualTo(5)
        }
    }

    private fun deployDapp(dappName: String, containerName: String, additionalSources: File? = null) {
        testLogger.info("Deploy new dapp $dappName")

        val rellConfig = compileDapp(dappName, additionalSources)

        var blockchainRid: BlockchainRid? = null
        rellConfig.config.chains.forEach { chain ->
            testLogger.info { "Adding test dapp $dappName" }
            chain.configs.forEach { (height, config) ->
                node3Db.awaitNewBlock()

                val configGtv = getBaseConfig(config)
                blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configGtv, cryptoSystem)
                dapps[dappName] = blockchainRid!!
                testLogger.info { "Proposing a blockchain ${blockchainRid?.toHex()} with config at height $height" }

                node1.c0.transactionBuilder()
                        .proposeBlockchainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(configGtv), "dapp", containerName, "")
                        .postTransactionUntilConfirmed("Propose dapp $blockchainRid")

                if (containerName == systemContainer) {
                    voteOnAllProposals(node2.provider)
                    voteOnAllProposals(node3.provider)
                }
            }
        }

        // Asserting that node1/node2/node3 are signers of newly added blockchain
        awaitQueryResult {
            assert(node1.c0.getBlockchainSigners(blockchainRid!!).size).isEqualTo(3)
            assert(node2.c0.getBlockchainSigners(blockchainRid!!).size).isEqualTo(3)
            assert(node3.c0.getBlockchainSigners(blockchainRid!!).size).isEqualTo(3)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        testLogger.info("Asserting that subnode container(s) launched")
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            val runningSubnodes = all.filter { it.image().contains("chromia-subnode") && it.state() == "running" }
            assert(runningSubnodes.size).isEqualTo(2 * numberOfMasterNodes)
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
                Assertions.assertEquals(foobarResourceLimits.ramBytes(), res.hostConfig()?.memory())
                Assertions.assertEquals(foobarResourceLimits.cpuQuota(), res.hostConfig()?.cpuQuota())
                Assertions.assertEquals(foobarResourceLimits.ioReadBytes(), res.hostConfig().blkioDeviceReadBps()[0].rate().toLong())
                Assertions.assertEquals(foobarResourceLimits.ioWriteBytes(), res.hostConfig().blkioDeviceWriteBps()[0].rate().toLong())
            }
        }
    }

    @Test
    @Order(10)
    fun `Transactions can be sent to test-dapp`() {
        assertThatDappProcessesTx(dapps["test-dapp"]!!, "add_city", "Heraklion", "get_cities")
    }

    @Test
    @Order(11)
    fun `Transactions can be sent to test-dapp2`() {
        assertThatDappProcessesTx(dapps["test-dapp2"]!!, "add_book", "Mastering Bitcoin", "get_books")
    }

    private fun assertThatDappProcessesTx(brid: BlockchainRid, txOp: String, txArg: String, query: String) {
        testLogger.info("Send TX to new dapp ${brid.toHex()} and fetch data")
        dappTxs[brid] = node2.tx(brid, txOp, gtv(txArg)).first
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val cities = awaitQueryResult { node.client(brid).query(query, gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assert(cities).containsExactly(txArg)
            }
        }
    }

//    @Test
//    @Order(12)
    fun `Reconfiguration of test-dapp2`(@TempDir tmpIccfSources: File) {

        fun getAssertingParam(): Long {
            val brid = dapps["test-dapp2"]!!
            val height = awaitQueryResult { node1.client(brid).currentBlockHeight() }!!
            assertTrue(height > 0)
            val config0 = node1.c0.nmGetBlockchainConfiguration(brid, height)
            assertNotNull(config0)
            return GtvDecoder.decodeGtv(config0).asDict()["blockstrategy"]!!["maxblocktransactions"]!!.asInteger()
        }

        // initial value
        assertEquals(500L, getAssertingParam())

        // reconfiguring test-dapp2
        File("../chain0-impl/rell/src/iccf").copyRecursively(tmpIccfSources.resolve("iccf"))
        updateDapp("test-dapp2", tmpIccfSources)

        // new value
        awaitUntilAsserted {
            assertEquals(1000L, getAssertingParam())
        }
    }

    private fun updateDapp(dappName: String, additionalSources: File? = null) {
        testLogger.info("Update dapp $dappName")

        val rellConfig = compileDapp("$dappName-update", additionalSources)
                .config.chains.first().configs.entries.first().value
        val config = GtvEncoder.encodeGtv(getBaseConfig(rellConfig))

        node1.c0.transactionBuilder()
                .proposeConfigurationOperation(node1.providerPubkey, dapps[dappName]!!, config, "")
                .postTransactionUntilConfirmed("Propose $dappName config")
    }

    @Test
    @Order(13)
    fun `Legacy anchoring can anchor blocks`() {
        assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dapps["test-dapp"]!!)
        assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dapps["test-dapp2"]!!)
    }

    private fun assertThatDappBlocksAreAnchoredWithLegacyAnchoring(dappBrid: BlockchainRid) {
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val lastAnchoredBlock = awaitQueryResult {
                    node.c0.getLastLegacyAnchoredBlock(dappBrid)
                }
                assert(lastAnchoredBlock).isNotNull()

                val dappChainBlock = awaitQueryResult {
                    node.client(dappBrid).blockAtHeight(lastAnchoredBlock!!.height)
                }
                assert(dappChainBlock).isNotNull()

                assert(dappChainBlock!!.rid).isEqualTo(lastAnchoredBlock!!.blockRid)
            }
        }
    }

    @Test
    @Order(14)
    fun `Blocks can be anchored`() {
        val anchoringChainBrid = node1.c0.cmGetClusterInfo("system").anchoringChain

        assertThatBlocksAreAnchored(BlockchainRid(anchoringChainBrid), dapps["test-dapp"]!!)
        assertThatBlocksAreAnchored(BlockchainRid(anchoringChainBrid), dapps["test-dapp2"]!!)
    }

    @Test
    @Order(15)
    fun `Cluster anchoring chain blocks are anchored in system anchoring chain`() {
        val systemAnchoringChainBrid = node1.c0.cmGetSystemAnchoringChain()
        assert(systemAnchoringChainBrid).isNotNull()
        val clusterAnchoringChainBrid = node1.c0.cmGetClusterInfo("system").anchoringChain

        assertThatBlocksAreAnchored(BlockchainRid(systemAnchoringChainBrid!!), BlockchainRid(clusterAnchoringChainBrid))
    }

    private fun assertThatBlocksAreAnchored(anchoringChainBrid: BlockchainRid, sourceBrid: BlockchainRid) {
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val lastAnchoredBlock = awaitQueryResult {
                    node.client(anchoringChainBrid).getLastAnchoredBlock(sourceBrid)
                }
                assert(lastAnchoredBlock).isNotNull()

                val sourceChainBlock = awaitQueryResult {
                    node.client(sourceBrid).blockAtHeight(lastAnchoredBlock!!.blockHeight)
                }
                assert(sourceChainBlock).isNotNull()

                assert(sourceChainBlock!!.rid).isEqualTo(lastAnchoredBlock!!.blockRid)

                val sourceWitness = BaseBlockWitness.fromBytes(sourceChainBlock.witness.data)
                val anchorWitness = BaseBlockWitness.fromBytes(lastAnchoredBlock.witness.data)

                assert(sourceWitness.getSignatures().size).isEqualTo(anchorWitness.getSignatures().size)
                sourceWitness.getSignatures().forEach { sourceSignature ->
                    assert(anchorWitness.getSignatures().any {
                        it.subjectID.contentEquals(sourceSignature.subjectID) && it.data.contentEquals(sourceSignature.data)
                    }).isTrue()
                }
            }
        }
    }

    @Test
    @Order(16)
    fun `ICMF messages are delivered`() {
        val receiverDapp = dapps["test-dapp2"]!!
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val cities = awaitQueryResult { node.client(receiverDapp).query("get_icmf_cities", gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assert(cities).containsExactly("Heraklion")
            }
        }
    }

//    @Test
//    @Order(17)
    fun `ICCF transfers are validated`() {
        val sourceDapp = dapps["test-dapp"]!!
        val targetDapp = dapps["test-dapp2"]!!

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
        iccfMaterial.txBuilder.addOperation("iccf_transfer", gtv(sourceDapp), actualTxToProve.toGtv())
                .postTransactionUntilConfirmed("iccf_transfer")
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val cities = awaitQueryResult { node.client(targetDapp).query("get_iccf_cities", gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assert(cities).containsExactly("Heraklion")
            }
        }
    }

    private fun queryContainerResourceLimits(): Array<ResourceLimit> {
        return node1.c0.nmGetContainerLimits(foobarContainer)
                .mapNotNull {
                    ResourceLimitFactory.fromPair(it.toPair())
                }.toTypedArray()
    }

    private fun getBaseConfig(config: RellPostAppChainConfig): Gtv {
        val fullConfig = config.gtvConfig.asDict().toMutableMap()
        fullConfig.remove("signers")
        return gtv(fullConfig)
    }

    private val PostchainContainer.c0 get() = client(chain0Brid)

    val PostchainContainer.providerPubkey get() = provider.pubKey.data
}
