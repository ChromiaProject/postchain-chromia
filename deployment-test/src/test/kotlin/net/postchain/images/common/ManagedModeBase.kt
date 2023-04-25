package net.postchain.images.common

import assertk.assertions.contains
import assertk.assertions.isNotEmpty
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import mu.KotlinLogging
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetPeerInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.chain0.proposal.getProposalsSince
import net.postchain.chain0.proposal.voting.makeVoteOperation
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.images.directory1.awaitQueryResult
import net.postchain.images.directory1.getResolvedDockerHost
import net.postchain.images.directory1.saveSubnodeLogs
import net.postchain.images.directory1.setupMasterNodeConfig
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellPostAppChainConfig
import net.postchain.rell.tools.runcfg.RellPostAppCliConfig
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterAll
import org.mandas.docker.client.DockerClient
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Base class for managed mode tests
open class ManagedModeBase(rellFolder: String) {

    val cryptoSystem = Secp256K1CryptoSystem()
    val resolvedDockerHost = getResolvedDockerHost()
    protected val dockerClient: DockerClient = DockerClientFactory.create()

    val testLogger = KotlinLogging.logger("TestLogger")
    val node1Logger = KotlinLogging.logger("Node1Logger")
    val node2Logger = KotlinLogging.logger("Node2Logger")
    val node3Logger = KotlinLogging.logger("Node3Logger")

    val network: Network = Network.newNetwork()

    val postgres: ChromaWayPostgresContainer = ChromaWayPostgresContainer(DockerImages.postgresImage())
            .withNetwork(network)
            .withEnv("POSTGRES_PASSWORD", "postchain")
            .withEnv("POSTGRES_USER", "postchain")
            .withEnv("POSTGRES_DB", "postchain")

    lateinit var node1: PostchainContainer
    lateinit var node2: PostchainContainer
    lateinit var node3: PostchainContainer

    lateinit var clusterAnchoringBrid: BlockchainRid
    lateinit var systemAnchoringBrid: BlockchainRid
    val systemContainer = "system"
    val dapps = mutableMapOf<String, BlockchainRid>()

    fun nodes() = arrayOf(node1, node2, node3)

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
        dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).forEach {
            if (it.image().contains("chromia-subnode")) {
                dockerClient.stopContainer(it.id(), 0)
                dockerClient.removeContainer(it.id())
            }
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
                .withExposedPorts(50051, appConfig.getInt("api.port"))
                .withClasspathResourceMapping("${this::class.java.getResource(configDir)!!.path.substringAfter("test-classes/")}/${hostName}", "/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping(this::class.java.getResource("/log")!!.path.substringAfter("test-classes/"), "/opt/chromaway/postchain/log", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DEBUG", "true")
                .withEnv("POSTCHAIN_PCU", true.toString())
                .withEnv("POSTCHAIN_CONFIG", "/config/node-config.properties")
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withLogConsumer(logConsumer)
                .withCommand("run-server")
    }

    var chain0Config: File
    lateinit var chain0Brid: BlockchainRid

    val PostchainContainer.c0 get() = client(chain0Brid)

    val PostchainContainer.providerPubkey get() = provider.pubKey.data

    init {
        val runConf = this::class.java.getResource("run.xml")!!
        val configFiles = RellRunConfigGenerator.generateCli(
                File(rellFolder),
                File(runConf.toURI()),
                RellVersions.VERSION,
                false
        ).let {
            RellRunConfigGenerator.buildFiles(it.config)
        }
        val gtvFile = kotlin.io.path.createTempFile(suffix = ".gtv")
        configFiles["blockchains/0/0.gtv"]!!.write(gtvFile.toFile())
        chain0Config = gtvFile.toFile()
    }

    lateinit var node1Db: ChainDatabaseCommunicator
    lateinit var node2Db: ChainDatabaseCommunicator
    lateinit var node3Db: ChainDatabaseCommunicator

    private lateinit var channel1: ManagedChannel
    private lateinit var channel2: ManagedChannel
    private lateinit var channel3: ManagedChannel

    fun stopNodes() {
        if (::channel1.isInitialized) channel1.shutdownNow()
        if (::channel2.isInitialized) channel2.shutdownNow()
        if (::channel3.isInitialized) channel3.shutdownNow()
        stopContainers(*nodes())
        postgres.stop()
    }

    fun startNodesAndChain0() {
        testLogger.info { "Starting nodes..." }
        postgres.start()
        startContainers(*nodes())

        // node1
        channel1 = createChannel(node1).usePlaintext().build()
        chain0Brid = startBlockchain(channel1, chain0Config)
                .let { BlockchainRid.buildFromHex(it) }
        testLogger.info("Chain0 bc-rid: ${chain0Brid.toHex()}")
        node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)

        // node2
        if (::node2.isInitialized) {
            channel2 = createChannel(node2).usePlaintext().build()
            addPeer(channel2, node1)
            startBlockchain(channel2, chain0Config)
            node2Db = postgres.createChainDatabaseCommunicator(0, node2.appConfig.databaseSchema)
        }

        // node3
        if (::node3.isInitialized) {
            channel3 = createChannel(node3).usePlaintext().build()
            addPeer(channel3, node1)
            startBlockchain(channel3, chain0Config)
            node3Db = postgres.createChainDatabaseCommunicator(0, node3.appConfig.databaseSchema)
        }
    }

    private fun createChannel(target: PostchainContainer) =
            ManagedChannelBuilder.forTarget("${target.host}:${target.getMappedPort(50051)}")

    private fun addPeer(channel: ManagedChannel, peer: PostchainContainer) {
        val service = PeerServiceGrpc.newBlockingStub(channel)
        service.addPeer(
                AddPeerRequest.newBuilder()
                        .setHost(peer.nodeHost)
                        .setPort(peer.nodePort)
                        .setPubkey(peer.pubkey.hex())
                        .build()
        )
    }

    private fun startBlockchain(channel: ManagedChannel, config: File): String {
        return PostchainServiceGrpc.newBlockingStub(channel)
                .initializeBlockchain(
                        InitializeBlockchainRequest.newBuilder()
                                .setChainId(0)
                                .setGtv(ByteString.copyFrom(config.readBytes()))
                                .build()
                ).brid
    }

    fun compileDapp(dappName: String = "test-dapp", additionalSources: File? = null, runXmlFileOverrides: Map<String, String> = mapOf(), runXmlFile: String = "run.xml"): RellPostAppCliConfig {
        val dappSources = this::class.java.classLoader.getResource(dappName)!!
        val applicationFolder = if (additionalSources != null) {
            File(dappSources.toURI()).copyRecursively(additionalSources, true)
            additionalSources
        } else {
            File(dappSources.toURI())
        }
        return compileChain("$dappName/$runXmlFile", applicationFolder, runXmlFileOverrides)
    }

    fun compileChain(runXmlFile: String, rellSources: File, runXmlFileOverrides: Map<String, String> = mapOf()): RellPostAppCliConfig {
        val runFile: File = getRunFileWithOverrides(runXmlFile, runXmlFileOverrides)
        return RellRunConfigGenerator.generateCli(
                rellSources,
                runFile,
                RellVersions.VERSION,
                false
        ).apply {
            RellRunConfigGenerator.buildFiles(this.config)
        }
    }

    private fun getRunFileWithOverrides(runXmlFile: String, runXmlFileOverrides: Map<String, String>): File {
        val runConf = requireNotNull(this::class.java.classLoader.getResource(runXmlFile))
        val srcFile = File(runConf.toURI())
        if (runXmlFileOverrides.isEmpty()) {
            return srcFile
        }
        var fileContents = FileUtils.readFileToString(srcFile, StandardCharsets.UTF_8)
        runXmlFileOverrides.forEach { (key, value) ->
            fileContents = fileContents.replace(key, value)
        }
        val dstFile = File.createTempFile("run-file-override-", ".xml")
        FileUtils.writeStringToFile(dstFile, fileContents, StandardCharsets.UTF_8)
        return dstFile
    }

    fun getBaseConfig(config: RellPostAppChainConfig): Gtv {
        val fullConfig = config.gtvConfig.asDict().toMutableMap()
        fullConfig.remove("signers")
        return GtvFactory.gtv(fullConfig)
    }

    fun assertAnchoringChainProperties() {
        val systemChains = node1.c0.nmComputeBlockchainInfoList(node1.nodeKeyPair.pubKey.data)
                .filter { it.system }.map { BlockchainRid(it.rid) }
        assertEquals(3, systemChains.size)

        // Getting cluster anchoring chain for system cluster via CM API
        clusterAnchoringBrid = BlockchainRid(node1.c0.cmGetClusterInfo("system").anchoringChain)
        // Asserting cluster anchoring chain is in system_chains list of NP API
        assertk.assert(systemChains.map { it }).contains(clusterAnchoringBrid)
        testLogger.info("Cluster anchor chain bc-rid: $clusterAnchoringBrid")

        systemAnchoringBrid = BlockchainRid(node1.c0.cmGetSystemAnchoringChain()!!)
        assertk.assert(systemChains.map { it }).contains(systemAnchoringBrid)
        testLogger.info("System anchor chain bc-rid: $systemAnchoringBrid")
    }

    fun assertAnchoringChainsFunctional() {
        listOf(chain0Brid, clusterAnchoringBrid, systemAnchoringBrid).forEach {
            val currentHeight = awaitQueryResult { node1.client(it).currentBlockHeight() }!!
            awaitQueryResult {
                assertTrue(node1.client(it).currentBlockHeight() > (currentHeight + 1))
            }
        }
    }

    fun deployDapp(dappName: String, containerName: String, additionalSources: File? = null, runFileOverrides: Map<String, String> = mapOf(), expectedSigners: List<PostchainContainer> = listOf(node1, node2, node3)) {
        testLogger.info("Deploy new dapp $dappName")

        val rellConfig = compileDapp(dappName, additionalSources, runFileOverrides)

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

        // Asserting that node1, node2, node3 are signers of newly added blockchain
        assertChainSigners(blockchainRid!!, *expectedSigners.toTypedArray())
    }

    fun assertChainSigners(blockchainRid: BlockchainRid, vararg nodes: PostchainContainer) {
        awaitQueryResult {
            val currentHeight = node1.client(blockchainRid).currentBlockHeight()
            val actual = node1.c0.cmGetPeerInfo(blockchainRid.data, currentHeight).map { PubKey(it) }.toSet()
            val expected = nodes.map { it.pubkey }.toSet()
            assertEquals(expected, actual)
        }
    }

    fun voteOnAllProposals(provider: KeyPair) {
        val proposals = awaitQueryResult {
            val result = node1.c0.getProposalsSince(RowId(0))
            assertk.assert(result).isNotEmpty()
            return@awaitQueryResult result
        }!!

        proposals.sortedBy { it.rowid.id }.forEach {
            node1.client(chain0Brid, listOf(provider)).transactionBuilder()
                    .makeVoteOperation(provider.pubKey.data, it.rowid.id, true)
                    .postTransactionUntilConfirmed("provider ${provider.pubKey.hex()} vote on ${it.rowid}, ${it.proposalType}")
        }
    }
}
