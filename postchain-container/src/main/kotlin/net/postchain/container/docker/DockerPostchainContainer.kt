package net.postchain.container.docker

import net.postchain.container.PostchainContainer
import org.mandas.docker.client.messages.ContainerConfig

data class DockerPostchainContainer(val config: ContainerConfig, override val name: String): PostchainContainer
