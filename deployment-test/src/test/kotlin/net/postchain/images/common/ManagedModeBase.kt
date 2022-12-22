package net.postchain.images.common

import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import mu.KotlinLogging
import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.images.directory1.setupMasterNodeConfig
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellPostAppCliConfig
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File

// Base class for managed mode tests
open class ManagedModeBase(rellFolder: String) {

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

    val node1: PostchainContainer = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger), 9871, 7740,
    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"))
    val node2: PostchainContainer = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger), 9872, 7741,
    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"))
    val node3: PostchainContainer = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger), 9873, 7742,
    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"))

    private fun postchainServer(hostName: String, logConsumer: Slf4jLogConsumer?, messagePort: Int, apiPort: Int, provider: KeyPair): PostchainContainer {
        val appConfig = setupMasterNodeConfig(this::class.java.getResource("config/$hostName/node-config.properties")!!)
        return PostchainContainer(
                DockerImages.chromiaServerImage(),
                appConfig,
                startupMsg = "Postchain server started, listening on 50051",
                nodeHost = hostName,
                nodePort = messagePort,
                provider = provider
        )
                .withNetworkAliases(hostName)
                .withNetwork(this@ManagedModeBase.network)
                .withFixedExposedPort(apiPort, apiPort) // Must be fixed so subnode can connect
                .withExposedPorts(50051)
                .withClasspathResourceMapping("${this::class.java.getResource("config")!!.path.substringAfter("test-classes/")}/${hostName}", "/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_CONFIG", "/config/node-config.properties")
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withLogConsumer(logConsumer)
    }


    var chain0Config: File
    lateinit var brid: BlockchainRid

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
        stopContainers(node1, node2, node3)
        postgres.stop()
    }

    fun startNodesAndChain0() {
        testLogger.info { "Starting nodes..." }
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

    fun compileDapp(dappName: String = "test-dapp", additionalSources: File? = null): RellPostAppCliConfig {
        val dappSources = this::class.java.classLoader.getResource(dappName)!!
        val applicationFolder = if (additionalSources != null) {
            File(dappSources.toURI()).copyRecursively(additionalSources)
            additionalSources
        } else {
            File(dappSources.toURI())
        }
        val runConf = this::class.java.classLoader.getResource("$dappName/run.xml")!!
        return RellRunConfigGenerator.generateCli(
                applicationFolder,
                File(runConf.toURI()),
                RellVersions.VERSION,
                false
        ).apply {
            RellRunConfigGenerator.buildFiles(this.config)
        }
    }
}