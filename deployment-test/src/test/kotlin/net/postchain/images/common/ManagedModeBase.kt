package net.postchain.images.common

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Capability
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import mu.KotlinLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetPeerInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common_proposal.getCommonProposalsRange
import net.postchain.chain0.common_proposal.makeCommonVoteV65Operation
import net.postchain.chain0.model.BlockchainState
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetBlockchainConfigurationInfo
import net.postchain.chain0.nm_api.nmGetBlockchainState
import net.postchain.chain0.proposal.getRelevantProposals
import net.postchain.chain0.proposal.voting.makeVoteOperation
import net.postchain.chain0.proposal_blockchain.findBlockchainRid
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.d1.rell.anchoring_chain_common.getAnchoredBlockAtHeight
import net.postchain.d1.rell.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.d1.rell.anchoring_chain_common.isBlockAnchored
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.Gtx
import net.postchain.images.directory1.awaitQueryResult
import net.postchain.images.directory1.awaitUntilAsserted
import net.postchain.images.directory1.getMasterContainerUserAndGroups
import net.postchain.images.directory1.getResolvedDockerHost
import net.postchain.images.directory1.listSubContainersCmd
import net.postchain.images.directory1.saveSubnodeLogs
import net.postchain.images.directory1.setupMasterNodeConfig
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.StartBlockchainRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File

// Base class for managed mode tests
open class ManagedModeBase {

    protected val cryptoSystem = Secp256K1CryptoSystem()

    val testLogger = KotlinLogging.logger("TestLogger")
    open val logsSubdir = ""

    val network: Network = Network.newNetwork()

    val postgres: ChromaWayPostgresContainer = ChromaWayPostgresContainer(DockerImages.postgresImage())
            .withNetwork(network)

    lateinit var node1: PostchainContainer
    lateinit var node2: PostchainContainer
    lateinit var node3: PostchainContainer
    lateinit var node4: PostchainContainer
    lateinit var node5: PostchainContainer

    val resolvedDockerHost = getResolvedDockerHost()
    protected val dockerClient: DockerClient = DockerClientFactory.create()
    protected val dapps = mutableMapOf<String, BlockchainRid>()
    lateinit var clusterAnchoringBrid: BlockchainRid
    lateinit var systemAnchoringBrid: BlockchainRid
    protected val dappTxs = mutableMapOf<BlockchainRid, Gtx>()
    protected val systemCluster = "system"
    protected val systemContainer = "system"

    var chain0Config: String = this::class.java.getResource("/directory1deployment/manager.xml")!!.readText()
    lateinit var chain0Brid: BlockchainRid
    lateinit var ecBrid: BlockchainRid
    private var nodeDbs = mutableMapOf<PostchainContainer, ChainDatabaseCommunicator>()

    protected lateinit var tcBrid: BlockchainRid
    protected val PostchainContainer.c0 get() = client(chain0Brid)
    protected val PostchainContainer.ec get() = client(ecBrid)
    protected val PostchainContainer.tc get() = client(tcBrid)
    protected val PostchainContainer.providerPubkey get() = provider.pubKey.data

    fun nodes() = buildList {
        if (::node1.isInitialized && node1.isRunning) add(node1)
        if (::node2.isInitialized && node2.isRunning) add(node2)
        if (::node3.isInitialized && node3.isRunning) add(node3)
        if (::node4.isInitialized && node4.isRunning) add(node4)
        if (::node5.isInitialized && node5.isRunning) add(node5)
    }.toTypedArray()

    fun breakdown() {
        saveSubnodeLogs(dockerClient, logsSubdir)
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
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
        }

        if (!File(PostchainContainer.MOUNT_DIR).deleteRecursively()) {
            testLogger.error("Unable to clear mount directory")
        }
    }

    fun removeSubnodeContainers() {
        dockerClient.listSubContainersCmd().withStatusFilter(listOf("running")).exec().forEach {
            dockerClient.killContainerCmd(it.id).withSignal("SIGKILL").exec()
        }
        dockerClient.listSubContainersCmd().exec().forEach {
            dockerClient.removeContainerCmd(it.id).exec()
        }
    }

    fun postchainServer(hostName: String, logConsumer: Slf4jLogConsumer?, provider: KeyPair, configDir: String): PostchainContainer {
        val appConfig = setupMasterNodeConfig(this::class.java.getResource("$configDir/$hostName/node-config.properties")!!)

        return PostchainContainer(
                DockerImages.chromiaServerImage(),
                appConfig,
                startupMsg = "Postchain server started, listening on 50051",
                nodeHost = hostName,
                provider = provider
        )
                .withNetworkAliases(hostName)
                .withNetwork(this@ManagedModeBase.network)
                .withExposedPorts(*exposedPorts(appConfig))
                .withClasspathResourceMapping("${this::class.java.getResource(configDir)!!.path.substringAfter("test-classes/")}/${hostName}", "/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping(this::class.java.getResource("/log")!!.path.substringAfter("test-classes/"), "/opt/chromaway/postchain", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DEBUG", "true")
                .withEnv("POSTCHAIN_CONFIG", "/config/node-config.properties")
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("POSTCHAIN_SUBNODE_IDLE_TIMEOUT_MS", 30_000.toString())
                .withLogConsumer(logConsumer)
                .withCommand("run-server")
                .withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!
                            .withCapDrop(Capability.ALL)
                            .withCapAdd(Capability.CHOWN, Capability.FOWNER, Capability.DAC_OVERRIDE)
                            .withSecurityOpts(listOf("no-new-privileges:true"))
                            .apply {
                                getMasterContainerUserAndGroups()?.let { (userSpec, groups) ->
                                    cmd.withUser(userSpec)
                                    withGroupAdd(groups)
                                }
                            }
                }
    }

    fun getDb(node: PostchainContainer): ChainDatabaseCommunicator =
            nodeDbs.getOrPut(node) {
                postgres.createChainDatabaseCommunicator(0, node.appConfig.databaseSchema)
            }

    fun stopNodes() {
        stopContainers(*nodes())
        postgres.stop()
        nodeDbs.clear()
    }

    fun restartNode(node: PostchainContainer, containerProvider: (() -> PostchainContainer)? = null): PostchainContainer {
        stopContainers(node)
        val startNode = containerProvider?.invoke() ?: node
        startContainers(startNode)

        PostchainServiceGrpc.newBlockingStub(startNode.channel)
                .startBlockchain(
                        StartBlockchainRequest.newBuilder()
                                .setChainId(0)
                                .build()
                )

        return startNode
    }

    fun startNodesAndChain0() {
        testLogger.info { "Starting nodes..." }
        postgres.start()
        startContainers(*buildList {
            if (::node1.isInitialized) add(node1)
            if (::node2.isInitialized) add(node2)
            if (::node3.isInitialized) add(node3)
            if (::node4.isInitialized) add(node4)
            if (::node5.isInitialized) add(node5)
        }.toTypedArray())

        // node1
        chain0Brid = startBlockchain(node1.channel, chain0Config)
                .let { BlockchainRid.buildFromHex(it) }
        testLogger.info("Chain0 bc-rid: ${chain0Brid.toHex()}")

        // Other nodes if started
        nodes().filter { it != node1 }.forEach {
            startBlockchain(it.channel, chain0Config)
        }
    }

    fun startBlockchain(channel: ManagedChannel, config: String): String {
        return PostchainServiceGrpc.newBlockingStub(channel)
                .initializeBlockchain(
                        InitializeBlockchainRequest.newBuilder()
                                .setChainId(0)
                                .setGtv(ByteString.copyFrom(GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(config))))
                                .build()
                ).brid
    }

    protected fun assertAnchoringChainProperties() {
        val systemChains = node1.c0.nmComputeBlockchainInfoList(node1.nodeKeyPair.pubKey.data)
                .filter { it.system }.map { BlockchainRid(it.rid) }
        assertThat(systemChains.size).isEqualTo(3)

        // Getting cluster anchoring chain for system cluster via CM API
        clusterAnchoringBrid = BlockchainRid(node1.c0.cmGetClusterInfo(systemCluster).anchoringChain)
        // Asserting cluster anchoring chain is in system_chains list of NM API
        assertThat(systemChains.map { it }).contains(clusterAnchoringBrid)
        testLogger.info("Cluster anchor chain bc-rid: $clusterAnchoringBrid")

        systemAnchoringBrid = BlockchainRid(node1.c0.cmGetSystemAnchoringChain()!!)
        assertThat(systemChains.map { it }).contains(systemAnchoringBrid)
        testLogger.info("System anchor chain bc-rid: $systemAnchoringBrid")
    }

    protected fun voteOnAllProposals(providers: List<KeyPair>) {

        providers.forEach { provider ->

            val pendingProposals = awaitQueryResult {
                node1.c0.getRelevantProposals(0, Long.MAX_VALUE, true, provider.pubKey.data)
            }

            if (pendingProposals != null && pendingProposals.isNotEmpty()) {
                val client = node1.client(chain0Brid, listOf(provider)).transactionBuilder()
                pendingProposals.forEach { proposal ->
                    client.makeVoteOperation(provider.pubKey.data, proposal.rowid.id, true)
                }
                client.postTransactionUntilConfirmed("provider ${provider.pubKey} voted yes to all other providers proposals (${pendingProposals.map { it.rowid.id }})")
            }
        }
    }

    protected fun assertNumberOfChainSigners(blockchainRid: BlockchainRid, expected: Int) {
        awaitQueryResult {
            val currentHeight = node1.client(blockchainRid).currentBlockHeight()
            val actual = node1.c0.cmGetPeerInfo(blockchainRid.data, currentHeight).size
            assertThat(actual).isEqualTo(expected)
        }
    }

    protected fun assertChainSigners(blockchainRid: BlockchainRid, vararg nodes: PostchainContainer) {
        awaitQueryResult {
            val currentHeight = node1.client(blockchainRid).currentBlockHeight()
            val actual = node1.c0.cmGetPeerInfo(blockchainRid.data, currentHeight).map { PubKey(it) }.toSet()
            val expected = nodes.map { it.pubkey }.toSet()
            assertThat(actual).isEqualTo(expected)
        }
    }

    protected fun assertChainSigners(txRid: TxRid, vararg nodes: PostchainContainer): BlockchainRid =
            awaitQueryResult {
                val brid = node1.c0.findBlockchainRid(txRid.rid.hexStringToByteArray())?.let { BlockchainRid(it) }
                assertThat(brid).isNotNull()
                val currentHeight = node1.client(brid!!).currentBlockHeight()
                val actual = node1.c0.cmGetPeerInfo(brid.data, currentHeight).map { PubKey(it) }.toSet()
                val expected = nodes.map { it.pubkey }.toSet()
                assertThat(actual).isEqualTo(expected)
                brid
            }!!

    protected fun verifyBlockchainState(node: PostchainContainer, brid: BlockchainRid, expectedState: BlockchainState) {
        awaitUntilAsserted {
            assertEquals(expectedState.name, node.c0.nmGetBlockchainState(brid))
        }
    }

    protected fun assertBlockReanchored(
            brid: BlockchainRid,
            srcNode: PostchainContainer, srcAnchoringChain: BlockchainRid,
            dstNode: PostchainContainer, dstAnchoringChain: BlockchainRid,
            height: Long = -1L
    ) {
        awaitQueryResult {
            val blockRid = if (height == -1L) {
                srcNode.client(srcAnchoringChain).getLastAnchoredBlock(brid)!!.blockRid
            } else {
                srcNode.client(srcAnchoringChain).getAnchoredBlockAtHeight(brid, height)!!.blockRid
            }
            assertThat(dstNode.client(dstAnchoringChain).isBlockAnchored(brid, blockRid.data)).isTrue()
        }
    }

    protected fun assertThatDappProcessesTx(brid: BlockchainRid, txOp: String, txArg: String, query: String, txNode: PostchainContainer = node2, queryNodes: Array<PostchainContainer> = nodes()) {
        testLogger.info("Send TX to new dapp ${brid.toHex()} and fetch data")
        dappTxs[brid] = txNode.tx(brid, txOp, GtvFactory.gtv(txArg)).first
        assertDappQuery(brid, query, txArg, queryNodes)
    }

    protected fun assertDappQuery(brid: BlockchainRid, query: String, expectedResult: String, nodes: Array<PostchainContainer> = nodes()) {
        awaitUntilAsserted {
            nodes.forEach { node ->
                val cities = awaitQueryResult { node.client(brid).query(query, GtvFactory.gtv(mapOf())) }!!
                        .asArray().map { it.asString() }
                assertThat(cities).contains(expectedResult)
            }
        }
    }

    protected fun verifyICCF(chromiaClientProvider: ChromiaClientProvider, targetChainNodes: Array<PostchainContainer> = nodes()) {
        val sourceDapp = dapps["test_dapp"]!!
        val targetDapp = dapps["test_dapp2"]!!
        val txToProve = dappTxs[sourceDapp]!!

        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val iccfMaterial = IccfProofTxMaterialBuilder(chromiaClientProvider).build(
                TxRid(txToProve.gtxBody.calculateTxRid(hashCalculator).toHex()),
                txToProve.toGtv().merkleHash(hashCalculator),
                listOf(),
                sourceDapp,
                targetDapp
        )
        val actualTxToProve = iccfMaterial.updatedTx ?: txToProve

        testLogger.info("Posting ICCF proof to target chain")
        iccfMaterial.txBuilder.addOperation("iccf_transfer", actualTxToProve.toGtv())
                .postTransactionUntilConfirmed("iccf_transfer")
        assertDappQuery(targetDapp, "get_iccf_cities", "Heraklion", targetChainNodes)
    }

    protected fun deployDapp(dappName: String, containerName: String, icmfReceiver: ByteArray? = null, assertSigners: Array<PostchainContainer> = arrayOf(node1, node2, node3)) {
        testLogger.info("Deploy new dapp $dappName")

        val configGtv = compileDapp(dappName, icmfReceiver = icmfReceiver)

        val txRid = node1.c0.transactionBuilder().addNop()
                .proposeBlockchainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(configGtv), dappName, containerName, "")
                .postTransactionUntilConfirmed("Propose dapp $dappName")
                .txRid

        // Asserting signers of newly added blockchain
        dapps[dappName] = assertChainSigners(txRid, *assertSigners)
        testLogger.info { "Dapp $dappName deployed: ${dapps[dappName]}" }
    }

    protected fun getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node: PostchainContainer, blockchainRid: BlockchainRid): Set<Int> {
        val res = mutableSetOf<Int>()
        var current: Long? = 0L

        while (current != null) {
            val config = node.c0.nmGetBlockchainConfiguration(blockchainRid, current) ?: break
            res.add(GtvDecoder.decodeGtv(config).asDict()["blockstrategy"]!!["maxblocktransactions"]!!.asInteger().toInt())
            current = node.c0.nmFindNextConfigurationHeight(blockchainRid, current)
        }

        return res
    }

    protected fun getLastBlockConfigSigners(node: PostchainContainer, blockchainRid: BlockchainRid): List<WrappedByteArray> {
        val lastHeight = node1.client(blockchainRid).currentBlockHeight()
        return node.c0.nmGetBlockchainConfigurationInfo(blockchainRid, lastHeight)!!.signers
    }

    protected fun compileDapp(
            dappName: String,
            maxBlockTransactions: Int = 500,
            icmfReceiver: ByteArray? = null,
            faulty: Boolean = false,
            bugSupplier: (String) -> String = ::unknownModuleBug
    ): Gtv = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/$dappName.xml")!!.readText()
            .replace("<int>500</int>", "<int>$maxBlockTransactions</int>")
            .let {
                if (icmfReceiver != null) it.replace("<string>ICMF_SENDER_BRID</string>", "<bytea>${icmfReceiver.toHex()}</bytea>") else it
            }
            .let {
                if (faulty) bugSupplier(it) else it
            })

    protected fun findAndReplaceBugSupplier(config: String, oldValue: String, newValue: String): String {
        val patched = config.replace(oldValue, newValue)
        require(config != patched) { "Original config doesn't contain substring `$oldValue`" }
        return patched
    }

    protected fun unknownModuleBug(config: String) = findAndReplaceBugSupplier(
            config,
            "<string>net.postchain.gtx.StandardOpsGTXModule</string>",
            "<string>net.postchain.gtx.StandardOpsGTXModule</string>\n<string>unknown_module</string>"
    )

    protected fun makeVoteOnLatestProposal(node: PostchainContainer) {
        makeVoteOnLatestProposal(node.ec)
    }

    protected fun makeVoteOnLatestProposal(client: PostchainClient) {

        with(client) {
            val latestProposalId = getCommonProposalsRange(0, Long.MAX_VALUE, true).last().rowid

            transactionBuilder()
                    .makeCommonVoteV65Operation(latestProposalId, true)
                    .postTransactionUntilConfirmed("Voted in favour for proposal $latestProposalId")
        }
    }

    private fun exposedPorts(appConfig: AppConfig): Array<Int> {
        val ports = mutableListOf(50051)

        val restConfig = RestApiConfig.fromAppConfig(appConfig)

        if (restConfig.port > -1) {
            ports.add(restConfig.port)
        }

        if (restConfig.debugPort > -1) {
            ports.add(restConfig.debugPort)
        }

        return ports.toTypedArray()
    }
}
