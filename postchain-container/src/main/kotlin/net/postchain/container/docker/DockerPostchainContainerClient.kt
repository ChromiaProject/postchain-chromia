package net.postchain.container.docker

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.exceptions.DockerRequestException
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import mu.KLogging
import net.postchain.container.PostchainContainer
import net.postchain.container.PostchainContainerClient
import net.postchain.container.PostchainContainerClientFactory
import net.postchain.container.PostchainContainerConfig
import org.glassfish.jersey.client.RequestEntityProcessing

class DockerPostchainContainerClient(val client: DockerClient) : PostchainContainerClient {

    companion object : KLogging(), PostchainContainerClientFactory {
        override fun create() = DockerPostchainContainerClient(DefaultDockerClient
            .fromEnv()
            .useRequestEntityProcessing(RequestEntityProcessing.BUFFERED)
            .build())

    }
    override fun close() = client.close()

    override fun createContainer(config: PostchainContainerConfig): PostchainContainer {

        val volumes = config.volumes.map { (from, to) ->
            HostConfig.Bind
                .from(from)
                .to(to)
                .build()
        }

        /**
         * Rest API port binding.
         * If restApiConfig.restApiPort == -1 => no communication with API => no binding needed.
         * If restApiConfig.restApiPort > -1 subnodePort (in all containers) can always be set to e.g. 7740. We are in
         * control here and know that it is always free.
         * DockerPort must be both node and container specific and cannot be -1 or 0 (at least not allowed in Ubuntu.)
         * Therefore, use random port selection
         */
        val portBindings = mutableMapOf<String, List<PortBinding>>() // { dockerPort -> hostIp:hostPort }
        val apiPort = config.restApiConfig.port
        // rest-api-port
        val restApiPort = "${apiPort}/tcp"
        if (apiPort > 0) { // TODO: apiPort == 0 (Must know what port to expose, 0 indicates "any available port"
            portBindings[restApiPort] = listOf(PortBinding.of(config.hostName, apiPort))
        }
        // admin-rpc-port
        val adminPort = config.serverConfig.port
        val adminRpcPort = "${adminPort}/tcp"
        portBindings[adminRpcPort] = listOf(PortBinding.of(config.hostName, adminPort))

        // messaging port
        val messagingPort = config.appConfig.port
        portBindings["$messagingPort/tcp"] = listOf(PortBinding.of(config.hostName, messagingPort))

        /**
         * CPU:
         * $ docker run -it --cpu-period=100000 --cpu-quota=50000 ubuntu /bin/bash.
         * Here we leave cpu-period to its default value (100 ms) and control cpu-quota via the dataSource.
         */

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

        val dockerConfig = ContainerConfig.builder()
            .image(config.imageName)
            .hostConfig(hostConfig)
            .exposedPorts(portBindings.keys)
            .env("POSTCHAIN_DEBUG=${config.debug}")
            .env("POSTCHAIN_CONFIG=/config/${config.configFile.name}")
            .cmd("run-server", "-c", config.activeChainIds.joinToString(","), "--port", config.serverConfig.port.toString())
            .build()

        val container = config.containerName?.let { client.createContainer(dockerConfig, it) } ?: client.createContainer(dockerConfig)
        container.warnings()?.forEach { logger.warn(it) }
        return DockerPostchainContainer(dockerConfig, container.id()!!)
    }

    override fun removeContainer(name: String): Boolean {
        return tryCatch { client.removeContainer(name) }
    }

    override fun startContainer(name: String): Boolean {
        return tryCatch {
            val c = client.inspectContainer(name)
            if (c.state().running()) {
                println("Container $name already running")
                return@tryCatch
            }
            client.startContainer(name)
        }
    }

    override fun stopContainer(name: String): Boolean {
        return tryCatch { client.stopContainer(name, 10) }
    }

    override fun listContainers(): List<String> {
        return client.listContainers().map { it.id() }
    }

    override fun findContainer(name: String) =
        client.listContainers(DockerClient.ListContainersParam("name", name)).isNotEmpty()


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

    private fun tryCatch(f: () -> Unit): Boolean {
        return try {
            f()
            true
        } catch (e: Exception) {
            logger.error { e }
            false
        }
    }
}
