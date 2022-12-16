package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupSwitch
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.google.protobuf.ByteString
import io.grpc.*
import net.postchain.cli.util.TlsOptions
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.hexStringToByteArray
import net.postchain.container.PostchainContainerConfig
import net.postchain.container.docker.DockerPostchainContainerClient
import net.postchain.container.exception.ContainerStartupException
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.crypto.PubKey
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.config.TlsConfig
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import java.io.File
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant

sealed class RunnerOptions(name: String, help: String) : OptionGroup(name, help)
class DockerOptions : RunnerOptions("Docker options", "Options for the docker runner") {
    val name by option(help = "Container name").default("postchain")
    val image by option("--image", help = "Image name")
            .default("registry.gitlab.com/chromaway/postchain-distribution/chromaway/postchain-server:3.7.0")
    val volumes by option("-v", "--volume", help = "Volume mounts [<from>:<to>]")
            .convert { it.split(":", limit = 2) }
            .convert { it[0] to it[1] }
            .multiple()
}

class NativeOptions : RunnerOptions("Native node options", "Options for the native runner") {
    val postchainPath by option(help = "Path to the postchain executable", envvar = "POSTCHAIN_PATH").required()
}

class StartChainOptions : OptionGroup(name = "Blockchain options", help = "Blockchain to start immediately") {
    val chainId by option(help = "Internal chain id to start the blockchain for").int().default(0)
    val bcConfig by option(
            "-bc", "--blockchain-config",
            help = "Blockchain configuration to start directly (.xml or .gtv)"
    ).required()
}

class GenesisPeerOptions :
        OptionGroup(name = "Genesis peer options", help = "Peer information to a node in the network to connect to") {
    val genesisPubkey by option(help = "Public key to the genesis peer").convert { PubKey(it.hexStringToByteArray()) }
            .required()
    val genesisPeer by option(help = "Peer to add after container startup [<host>:<port>]")
            .convert { it.split(":", limit = 2) }
            .convert { it[0] to it[1] }
            .required()
}

class CommandStartNode : CliktCommand(
        name = "start",
        help = "Start a node locally from configuration file. Deprecated, start node with docker run according to documentation instead.",
        hidden = true
) {
    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val config by nodeConfigOption()

    private val runner by option(help = "How the node should be hosted (default: --docker)")
            .groupSwitch(
                    "--docker" to DockerOptions(),
                    "--native" to NativeOptions()
            )
            .defaultByName("--docker")

    private val host by option(help = "Hostname to access this node via rpc client").default("localhost")

    private val port by option(help = "Exposed port for rpc client").int().default(50051)

    private val startChain by StartChainOptions().cooccurring()

    private val genesisPeerOptions by GenesisPeerOptions().cooccurring()

    private val tlsOptions by TlsOptions().cooccurring()

    private val log4jFile by option(help = "File to configure log4j logging").file(canBeDir = false)

    private val debug by option(help = "Enable debug api").flag()

    override fun run() {
        val started = when (val it = runner) {
            is DockerOptions -> startDockerContainer(it)
            is NativeOptions -> startPostchainProcess(it)
        }
        if (!started) throw ContainerStartupException("Failed to start postchain")
        genesisPeerOptions?.let { addGenesisPeer(it) }
        startChain?.let { startBlockchain(it) }
    }

    private fun startDockerContainer(options: DockerOptions): Boolean {
        val name = options.name
        val image = options.image
        DockerPostchainContainerClient.create().use { client ->
            if (!client.findImage(image)) {
                println("Pulling image $image")
                client.pull(image)
            }
            val serverStartupMessage = "Postchain server started, listening on"
            if (client.findContainer(name)) {
                println("Container $name already exists, starting..")
                return client.startContainer(name, serverStartupMessage)
            }

            val conf = PostchainContainerConfig(
                    imageName = image,
                    containerName = name,
                    configFile = config,
                    serverConfig = tlsOptions?.let {
                        PostchainServerConfig(port, TlsConfig(it.certChainFile, it.privateKeyFile))
                    } ?: PostchainServerConfig(port),
                    volumes = mapOf(*options.volumes.toTypedArray()),
                    resourceLimits = ContainerResourceLimits.default(),
                    env = environment(),
                    debug = debug,
            )
            val container = client.createContainer(conf)
            return client.startContainer(container.name, serverStartupMessage)
                    .also { if (!it) client.removeContainer(container.name) }
        }
    }

    private fun startPostchainProcess(options: NativeOptions): Boolean {
        val args = mutableListOf(options.postchainPath, "run-server")
        log4jFile?.let {
            args.add("-Dlog4j2.configurationFile")
            args.add(it.absolutePath)
        }
        val processBuilder = ProcessBuilder(args)
        processBuilder.environment().apply {
            putAll(environment())
            put("POSTCHAIN_CONFIG", config.absolutePath)
            put("POSTCHAIN_SERVER_PORT", port.toString())
        }
        val process = processBuilder.start()
        return process.isAlive.also { alive ->
            val startTime = Instant.now()
            val serverStartupMessage = "Postchain server started, listening on"
            awaitProcess(process, serverStartupMessage, startTime, Duration.ofSeconds(5))
            if (!alive) println(
                    process.errorStream.bufferedReader().readText()
            ) else println("Started postchain with pid ${process.pid()}")
        }
    }

    private fun awaitProcess(
            process: Process,
            serverStartupMessage: String,
            startTime: Instant,
            timeOut: Duration = Duration.ofSeconds(5)
    ) {
        val br = InputStreamReader(process.inputStream).buffered()
        while (Instant.now() < startTime.plus(timeOut)) {
            if (br.readLine().contains(serverStartupMessage)) break
        }
    }

    private fun environment() = mapOf(
            "POSTCHAIN_DEBUG" to debug.toString(),
            "POSTCHAIN_INITIAL_CHAIN_IDS" to (startChain?.chainId ?: 0).toString()
    )

    private fun addGenesisPeer(options: GenesisPeerOptions) {
        withChannel {
            with(options) {
                val service = PeerServiceGrpc.newBlockingStub(it)
                try {
                    val reply = service.addPeer(
                            AddPeerRequest.newBuilder()
                                    .setPubkey(genesisPubkey.hex())
                                    .setHost(genesisPeer.first)
                                    .setPort(genesisPeer.second.toInt())
                                    .build()
                    )
                    println(reply.message)
                } catch (e: StatusRuntimeException) {
                    if (e.status.code == Status.ALREADY_EXISTS.code) {
                        println("Genesis peer information already exists in db")
                        return@withChannel
                    }
                    throw e
                }
            }
        }
    }

    private fun startBlockchain(options: StartChainOptions) {
        withChannel { channel ->
            val service = PostchainServiceGrpc.newBlockingStub(channel)

            val requestBuilder = InitializeBlockchainRequest.newBuilder()
                    .setChainId(options.chainId.toLong())
                    .setOverride(true)

            val configFile = File(options.bcConfig)
            when (configFile.extension) {
                "gtv" -> requestBuilder.gtv = ByteString.copyFrom(configFile.readBytes())
                "xml" -> requestBuilder.xml = configFile.readText()
                else -> throw IllegalArgumentException("File must be xml or gtv file")
            }
            val reply = service.initializeBlockchain(requestBuilder.build())
            println(reply.message)
        }
    }

    private fun withChannel(f: (Channel) -> Unit) {
        val channel = Grpc.newChannelBuilder("$host:$port", InsecureChannelCredentials.create()).build()
        f(channel)
        channel.shutdownNow()
    }
}
