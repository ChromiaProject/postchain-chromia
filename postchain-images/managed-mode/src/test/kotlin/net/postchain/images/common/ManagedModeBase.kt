package net.postchain.images.common

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import mu.KotlinLogging
import net.postchain.common.BlockchainRid
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.images.directory1.getResolvedDockerHost
import net.postchain.images.directory1.setupMasterNodeConfig
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellPostAppCliConfig
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.server.service.AddPeerRequest
import net.postchain.server.service.InitializeBlockchainRequest
import net.postchain.server.service.PeerServiceGrpc
import net.postchain.server.service.PostchainServiceGrpc
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import java.io.File

// Base class for managed mode tests
open class ManagedModeBase(rellFolder: String) {

    val consoleLogger = KotlinLogging.logger("TestLogger")
    private val logger = KotlinLogging.logger {}

    val node1Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node1")
    val node2Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node2")
    val node3Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node3")

    val network: Network = Network.newNetwork()

    val postgres: ChromaWayPostgresContainer = ChromaWayPostgresContainer()
        .withNetwork(network)

    val node1: PostchainContainer = postchainServer("node1", node1Logger, 9871, 7740)
    val node2: PostchainContainer = postchainServer("node2", node2Logger, 9872, 7741)
    val node3: PostchainContainer = postchainServer("node3", node3Logger, 9873, 7742)

    private fun postchainServer(hostName: String, logConsumer: Slf4jLogConsumer?, messagePort: Int, apiPort: Int) =
        PostchainContainer(
            DockerImageName.parse("chromaway/postchain-server:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest"),
            setupMasterNodeConfig(this::class.java.getResource("config/$hostName/node-config.properties")!!),
            startupMsg = "Postchain server started, listening on 50051",
            nodeHost = hostName,
            nodePort = messagePort
        )
            .withNetworkAliases(hostName)
            .withNetwork(this@ManagedModeBase.network)
            .withExposedPorts(50051, apiPort)
            .withClasspathResourceMapping("${this::class.java.getResource("config")!!.path.substringAfter("test-classes/")}/${hostName}", "/config", BindMode.READ_ONLY)
            .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
            .withLogConsumer(logConsumer)


    var chain0Config: File
    lateinit var brid: BlockchainRid

    init {
        val applicationFolder = this::class.java.getResource(rellFolder)!!
        val runConf = this::class.java.getResource("run.xml")!!
        val configFiles = RellRunConfigGenerator.generateCli(
            File(applicationFolder.toURI()),
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
        channel1.shutdownNow()
        channel2.shutdownNow()
        channel3.shutdownNow()
        stopContainers(node1, node2, node3)
        postgres.stop()
    }

    fun startNodesAndChain0() {
        consoleLogger.info { "Starting nodes..." }
        postgres.start()
        startContainers(node1, node2, node3)

        channel1 = createChannel(node1).usePlaintext().build()
        channel2 = createChannel(node2).usePlaintext().build()
        channel3 = createChannel(node3).usePlaintext().build()
        addPeer(channel2, node1)
        addPeer(channel3, node1)
        brid = startBlockchain(
            channel1,
            chain0Config
        ).let { BlockchainRid.buildFromHex(it) }
        startBlockchain(channel2, chain0Config)
        startBlockchain(channel3, chain0Config)

        node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
        node2Db = postgres.createChainDatabaseCommunicator(0, node2.appConfig.databaseSchema)
        node3Db = postgres.createChainDatabaseCommunicator(0, node3.appConfig.databaseSchema)
    }

    private fun createChannel(target: PostchainContainer) =
        ManagedChannelBuilder.forTarget("${target.host}:${target.getMappedPort(50051)}")

    private fun addPeer(channel: ManagedChannel, peer: PostchainContainer) {
        val service = PeerServiceGrpc.newBlockingStub(channel)
        service.addPeer(
            AddPeerRequest.newBuilder()
                .setHost(peer.nodeHost)
                .setPort(peer.nodePort)
                .setPubkey(peer.pubKey)
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

   fun compileDapp(): RellPostAppCliConfig {
       val applicationFolder = this::class.java.classLoader.getResource("test-dapp")!!
       val runConf = this::class.java.classLoader.getResource("test-dapp/run.xml")!!
       return RellRunConfigGenerator.generateCli(
           File(applicationFolder.toURI()),
           File(runConf.toURI()),
           RellVersions.VERSION,
           false
       ).apply {
           RellRunConfigGenerator.buildFiles(this.config)
       }
   }
}