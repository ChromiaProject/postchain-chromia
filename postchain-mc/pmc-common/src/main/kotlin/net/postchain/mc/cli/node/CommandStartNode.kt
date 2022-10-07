package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.groups.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.google.protobuf.ByteString
import io.grpc.Channel
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.restassured.RestAssured.port
import net.postchain.cli.util.*
import net.postchain.common.hexStringToByteArray
import net.postchain.container.PostchainContainerConfig
import net.postchain.container.docker.DockerPostchainContainerClient
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.crypto.PubKey
import net.postchain.server.config.PostchainServerConfig
import net.postchain.server.config.TlsConfig
import net.postchain.server.service.AddPeerRequest
import net.postchain.server.service.InitializeBlockchainRequest
import net.postchain.server.service.PeerServiceGrpc
import net.postchain.server.service.PostchainServiceGrpc
import java.io.File
import java.lang.Thread.sleep

sealed class RunnerOptions(name: String, help: String) : OptionGroup(name, help)
class DockerOptions : RunnerOptions("Docker options", "Options for the docker runner") {
    val name by option(help = "Container name").default("postchain")
    val image by option("--image", help = "Image name")
        .default("registry.gitlab.com/chromaway/postchain-distribution/chromaway/postchain-server:3.7.0-SNAPSHOT")
    val volumes by option("-v", "--volume", help = "Volume mounts [<from>:<to>]")
        .convert { it.split(":", limit = 2) }
        .convert { it[0] to it[1] }
        .multiple()
}

class PlainNodeOptions : RunnerOptions("Plain node options", "Options for the plain runner") {
    val postchainPath by option(help = "Path to the postchain executable", envvar = "POSTCHAIN_PATH").required()
    val logFile by option(help = "File to append logs to").file(canBeDir = false)
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
    help = "Start a node locally from configuration file"
) {
    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val config by nodeConfigOption()

    private val runner by option(help = "How the node should be hosted (default: --docker)")
        .groupSwitch(
            "--docker" to DockerOptions(),
            "--plain" to PlainNodeOptions()
        )
        .defaultByName("--docker")

    private val host by option(help = "Hostname to access this node via rpc client").default("localhost")

    private val port by option(help = "Exposed port for rpc client").int().default(50051)

    private val startChain by StartChainOptions().cooccurring()

    private val genesisPeerOptions by GenesisPeerOptions().cooccurring()

    private val tlsOptions by TlsOptions().cooccurring()

    private val debug by option(help = "Enable debug api").flag()

    override fun run() {
        val started = when (val it = runner) {
            is DockerOptions -> startDockerContainer(it)
            is PlainNodeOptions -> startPostchainProcess(it)
        }
        if (!started) throw RuntimeException("Failed to start postchain")
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
            if (client.findContainer(name)) {
                println("Container $name already exists, starting..")
                return client.startContainer(name)
            }

            val conf = PostchainContainerConfig(
                imageName = image,
                containerName = name,
                configFileName = config,
                serverConfig = tlsOptions?.let {
                    PostchainServerConfig(port, TlsConfig(it.certChainFile, it.privateKeyFile))
                } ?: PostchainServerConfig(port),
                volumes = mapOf(*options.volumes.toTypedArray()),
                resourceLimits = ContainerResourceLimits.default(),
                activeChainIds = listOf(startChain?.chainId ?: 0),
                debug = debug,
            )
            val container = client.createContainer(conf)
            return client.startContainer(container.name).also { if (!it) client.removeContainer(container.name) }
        }
    }

    private fun startPostchainProcess(options: PlainNodeOptions): Boolean {
        val args = mutableListOf(
            options.postchainPath, "run-server",
            "--node-config", config,
            "-c", "${startChain?.chainId ?: 0}",
            "--port", "$port",
        )
        if (debug) args.add("--debug")

        println("Starting process with args $args")
        val process = ProcessBuilder(args).apply {
            options.logFile?.let {
                redirectOutput(it)
                redirectErrorStream(true)
            }
        }
            .start()
        return process.isAlive.also { alive ->
            if (!alive) println(
                process.errorStream.bufferedReader().readText()
            ) else println("Started postchain with pid ${process.pid()}")
        }
    }

    private fun addGenesisPeer(options: GenesisPeerOptions) {
        sleep(5000) // TODO: Await start properly
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
        if (genesisPeerOptions == null) sleep(5000) // TODO: Await start properly
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
