package net.postchain.images.directory1

import mu.KotlinLogging
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_HOST_MOUNT_DIR
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_MASTER_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_USER
import net.postchain.containers.infra.ContainerNodeConfig.Companion.fullKey
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.parseConfig
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.DockerClient.LogsParam
import org.mandas.docker.client.messages.Container
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.file.Paths

val testLogger = KotlinLogging.logger("TestLogger")

internal fun getResolvedDockerHost(): URI? {
    return if (System.getenv("DOCKER_HOST") != null) {
        val dockerUri = URI(System.getenv("DOCKER_HOST"))
        // Pass docker host to master container with hostname resolved
        URI("${dockerUri.scheme}://${InetAddress.getByName(dockerUri.host).hostAddress}:${dockerUri.port}")
    } else {
        null
    }
}

internal fun setupMasterNodeConfig(resource: URL): AppConfig {
    val dockerHost = getResolvedDockerHost()
    val configOverrides = mutableMapOf<String, String>(
            fullKey(KEY_MASTER_HOST) to (dockerHost?.host ?: System.getProperty("DOCKER_HOST_MASTER", "172.17.0.1")),
            fullKey(KEY_SUBNODE_HOST) to (dockerHost?.host ?: System.getProperty("DOCKER_HOST_MASTER", "172.17.0.1")),
            fullKey(KEY_HOST_MOUNT_DIR) to PostchainContainer.MOUNT_DIR,
    )
    getSubnodeUser()?.apply {
        testLogger.info { "Postchain subnode user set to: $this" }
        configOverrides.put(fullKey(KEY_SUBNODE_USER), this)
    }
    testLogger.info { "Config overrides: $configOverrides" }
    return parseConfig(resource, configOverrides)
}

fun getSubnodeUser(): String? {
    return System.getenv("POSTCHAIN_SUBNODE_USER") ?: try {
        val unixSystem = com.sun.security.auth.module.UnixSystem()
        "${unixSystem.uid}:${unixSystem.gid}"
    } catch (e: Exception) {
        testLogger.warn("Unable to fetch current user id: $e")
        null
    } catch (le: LinkageError) {
        testLogger.warn("Fetching current user id is unsupported: $le")
        null
    }
}

internal fun saveSubnodeLogs(dockerClient: DockerClient, subdir: String = "") {
    val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
    all.filter { it.image().contains("chromia-subnode") }.forEach {
        Paths.get("logs", subdir, it.names()!!.first().replace("/", "") + ".log")
                .toFile()
                .appendText(getContainerLogs(dockerClient, it))
    }
}

internal fun getContainerLogs(dockerClient: DockerClient, container: Container) = dockerClient.logs(
        container.id(), LogsParam.stdout(), LogsParam.stderr()
).readFully()