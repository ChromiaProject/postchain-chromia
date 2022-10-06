package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.groups.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.google.protobuf.ByteString
import io.grpc.Channel
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import net.postchain.cli.util.*
import net.postchain.common.hexStringToByteArray
import net.postchain.container.PostchainContainerConfig
import net.postchain.container.docker.DockerPostchainContainerClient
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.crypto.PubKey
import net.postchain.server.config.PostchainServerConfig
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

class CliOptions : RunnerOptions("cli", "Options for the cli runner") {

}

class StartChainOptions : OptionGroup(name = "Blockchain options", help = "Blockchain to start immediately") {
    val chainId by option().int().default(0)
    val bcConfig by option("-bc", "--blockchain-config", help = "Blockchain config to start directly").required()

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

    private val runner by option(help = "How the node should be hosted")
        .groupSwitch(
            "--docker" to DockerOptions(),
            "--cli" to CliOptions()
        )
        .defaultByName("--docker")

    private val host by option(help = "Hostname to access this node").default("localhost")

    private val port by option(help = "Exposed port for rpc client").int().default(50051)

    private val startChain by StartChainOptions().cooccurring()

    private val genesisPeerOptions by GenesisPeerOptions().cooccurring()

    private val debug by option(help = "Enable debug api").flag(default = true)

    override fun run() {
        val started = when (val it = runner) {
            is DockerOptions -> startDockerContainer(it)
            is CliOptions -> throw NotImplementedError("Running a unix process is not implemented")
        }
        if (!started) throw RuntimeException("Failed to start container")
        genesisPeerOptions?.let { addGenesisPeer(it) }
        startChain?.let { startBlockchain(it) }
    }

    private fun startDockerContainer(options: DockerOptions): Boolean {
        val name = options.name
        val image = options.image
        DockerPostchainContainerClient.create().use { client ->
            if (!client.findImage(image)) client.pull(image)
            if (client.findContainer(name)) return client.startContainer(name)

            val conf = PostchainContainerConfig(
                imageName = image,
                containerName = name,
                configFileName = config,
                serverConfig = PostchainServerConfig(port),
                hostName = host,
                volumes = mapOf(*options.volumes.toTypedArray()),
                resourceLimits = ContainerResourceLimits.default(),
                activeChainIds = listOf(startChain?.chainId ?: 0),
                debug = debug,
            )
            val container = client.createContainer(conf)
            return client.startContainer(container.name).also { if (!it) client.removeContainer(container.name) }
        }
    }

    private fun addGenesisPeer(options: GenesisPeerOptions) {
        sleep(5000) // TODO: Await start properly
        withChannel {
            with(options) {
                val service = PeerServiceGrpc.newBlockingStub(it)
                val reply = service.addPeer(
                    AddPeerRequest.newBuilder()
                        .setPubkey(genesisPubkey.hex())
                        .setHost(genesisPeer.first)
                        .setPort(genesisPeer.second.toInt())
                        .build()
                )
                println(reply.message)
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
