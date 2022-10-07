package net.postchain.container.docker

import com.google.common.base.Charsets.UTF_8
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersFilterParam
import com.spotify.docker.client.exceptions.DockerRequestException
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import mu.KLogging
import net.postchain.container.PostchainContainer
import net.postchain.container.PostchainContainerClient
import net.postchain.container.PostchainContainerClientFactory
import net.postchain.container.PostchainContainerConfig
import net.postchain.container.exception.ContainerStartupException
import org.glassfish.jersey.client.RequestEntityProcessing
import java.time.Duration
import java.time.Instant

class DockerPostchainContainerClient(val client: DockerClient) : PostchainContainerClient {

    companion object : KLogging(), PostchainContainerClientFactory {
        override fun create() = DockerPostchainContainerClient(
            DefaultDockerClient
                .fromEnv()
                .useRequestEntityProcessing(RequestEntityProcessing.BUFFERED)
                .build()
        )

    }

    override fun close() = client.close()

    override fun createContainer(config: PostchainContainerConfig): PostchainContainer {

        val volumes = config.volumes.map { (from, to) ->
            HostConfig.Bind
                .from(from)
                .to(to)
                .build()
        }

        val portBindings = mutableMapOf<String, List<PortBinding>>() // { dockerPort -> hostIp:hostPort }
        // messaging port
        val messagingPort = config.appConfig.port
        portBindings["$messagingPort/tcp"] = listOf(PortBinding.of("0.0.0.0", messagingPort))

        // rest-api-port
        val apiPort = config.restApiConfig.port
        if (apiPort > 0) { // TODO: apiPort == 0 (Must know what port to expose, 0 indicates "any available port"
            portBindings["${apiPort}/tcp"] = listOf(PortBinding.of("0.0.0.0", apiPort))
        }

        // admin-rpc-port
        val adminPort = config.serverConfig.port
        portBindings["${adminPort}/tcp"] = listOf(PortBinding.of("0.0.0.0", adminPort))

        // Host config
        val resources = config.resourceLimits
        val hostConfig = HostConfig.builder()
            .appendBinds(*volumes.toTypedArray())
            .appendBinds(HostConfig.Bind.from(config.configFile.parentFile.absolutePath).to("/config").build())
            .portBindings(portBindings)
            .publishAllPorts(true)
            .apply {
                if (resources.hasRam()) memory(resources.ramBytes())
                if (resources.hasCpu()) {
                    cpuPeriod(resources.cpuPeriod())
                    cpuQuota(resources.cpuQuota())
                }
            }
            .build()

        val env = config.env.map { "${it.key}=${it.value}" }.toMutableList()
            .also {
                it.add("POSTCHAIN_CONFIG=/config/${config.configFile.name}")
                it.add("POSTCHAIN_SERVER_PORT=$adminPort")
            }
        val builder = ContainerConfig.builder()
            .image(config.imageName)
            .hostConfig(hostConfig)
            .exposedPorts(portBindings.keys)
        val dockerConfig = builder
            .env(env)
            .cmd(config.command)
            .build()

        val container =
            config.containerName?.let { client.createContainer(dockerConfig, it) } ?: client.createContainer(
                dockerConfig
            )
        container.warnings()?.forEach { logger.warn(it) }
        return DockerPostchainContainer(dockerConfig, container.id()!!)
    }

    override fun removeContainer(name: String): Boolean {
        return tryCatch {
            client.removeContainer(name)
            true
        }
    }

    override fun startContainer(name: String, awaitMessage: String): Boolean {
        return tryCatch {
            if (client.inspectContainer(name).state().running()) {
                println("Container $name already running")
                return@tryCatch true
            }
            val now = Instant.now()
            client.startContainer(name)
            return@tryCatch awaitServerStarted(name, awaitMessage, now)
        }
    }

    private fun awaitServerStarted(
        name: String,
        awaitMessage: String,
        startTime: Instant,
        timeOut: Duration = Duration.ofSeconds(5)
    ): Boolean {
        with(
            client.logs(
                name,
                DockerClient.LogsParam.stdout(),
                DockerClient.LogsParam.since(startTime.epochSecond.toInt()),
                DockerClient.LogsParam.follow()
            )
        ) {
            while (hasNext() && Instant.now() < startTime.plus(timeOut)) {
                val log = UTF_8.decode(next().content()).toString()
                if (log.contains(awaitMessage)) break
            }
        }
        val info = client.inspectContainer(name)
        if (!info.state().running()) {
            throw ContainerStartupException(client.logs(name, DockerClient.LogsParam.stderr()).readFully())
        }
        return true
    }

    override fun stopContainer(name: String): Boolean {
        return tryCatch {
            client.stopContainer(name, 10)
            true
        }
    }

    override fun listContainers(): List<String> {
        return client.listContainers().map { it.id() }
    }

    override fun findContainer(name: String) = tryCatch {
        client.listContainers(
            DockerClient.ListContainersParam.allContainers(),
            ListContainersFilterParam("name", name),
        ).isNotEmpty()
    }


    override fun findImage(imageName: String): Boolean {
        return try {
            client.listImages(DockerClient.ListImagesParam.filter("reference", imageName)).isNotEmpty()
        } catch (e: DockerRequestException) {
            println(e.message)
            false
        }
    }

    override fun pull(imageName: String) {
        return client.pull(imageName)
    }

    private fun tryCatch(f: () -> Boolean): Boolean {
        return try {
            f()
        } catch (e: Exception) {
            logger.error { e }
            false
        }
    }
}
