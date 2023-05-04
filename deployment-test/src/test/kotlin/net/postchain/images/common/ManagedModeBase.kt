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
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
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
import org.apache.commons.io.FileUtils
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File
import java.nio.charset.StandardCharsets

// Base class for managed mode tests
open class ManagedModeBase {

    val testLogger = KotlinLogging.logger("TestLogger")
    val node1Logger = KotlinLogging.logger("Node1Logger")
    val node2Logger = KotlinLogging.logger("Node2Logger")
    val node3Logger = KotlinLogging.logger("Node3Logger")

    val network: Network = Network.newNetwork()

    val postgres: ChromaWayPostgresContainer = ChromaWayPostgresContainer(DockerImages.postgresImage())
            .withNetwork(network)

    lateinit var node1: PostchainContainer
    lateinit var node2: PostchainContainer
    lateinit var node3: PostchainContainer

    fun nodes() = arrayOf(node1, node2, node3)

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

    var chain0Config: String = this::class.java.getResource("/directory1deployment/manager.xml")!!.readText()
    lateinit var chain0Brid: BlockchainRid

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

    private fun startBlockchain(channel: ManagedChannel, config: String): String {
        return PostchainServiceGrpc.newBlockingStub(channel)
                .initializeBlockchain(
                        InitializeBlockchainRequest.newBuilder()
                                .setChainId(0)
                                .setGtv(ByteString.copyFrom(GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(config))))
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

    private fun compileChain(runXmlFile: String, rellSources: File, runXmlFileOverrides: Map<String, String> = mapOf()): RellPostAppCliConfig {
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
}
