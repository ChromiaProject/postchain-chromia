package net.postchain.images.directory1

import mu.KLogger
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_HOST_MOUNT_DIR
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_MASTER_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.fullKey
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.parseConfig
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.DockerClient.LogsParam
import java.net.InetAddress
import java.net.URI
import java.net.URL

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
    val configOverrides = if (dockerHost != null) {
        mapOf(
            fullKey(KEY_MASTER_HOST) to dockerHost.host,
            fullKey(KEY_SUBNODE_HOST) to dockerHost.host,
            fullKey(KEY_HOST_MOUNT_DIR) to PostchainContainer.MOUNT_DIR,
        )
    } else {
        mapOf(
            fullKey(KEY_MASTER_HOST) to System.getProperty("DOCKER_HOST_MASTER", "172.17.0.1"),
            fullKey(KEY_SUBNODE_HOST) to System.getProperty("DOCKER_HOST_MASTER", "172.17.0.1"),
        )
    }
    return parseConfig(resource, configOverrides)
}

// Keeping this for future debugging purposes
internal fun printSubnodeLogs(dockerClient: DockerClient, logger: KLogger) {
    val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
    val subnodeContainer = all.find { it.image().contains("chromia-subnode") }
    if (subnodeContainer != null) {
        logger.info("------------------------- CONTAINER LOGS ---------------------\n")
        logger.info(
            dockerClient.logs(subnodeContainer.id(), LogsParam.stdout(), LogsParam.stderr(), LogsParam.tail(100))
                .readFully()
        )
        logger.info("\n------------------------- END OF CONTAINER LOGS --------------")
    } else {
        logger.info("No subcontainer is launched")
    }
}
