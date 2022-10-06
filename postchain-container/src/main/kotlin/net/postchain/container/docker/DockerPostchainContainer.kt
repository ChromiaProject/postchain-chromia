package net.postchain.container.docker

import com.spotify.docker.client.messages.ContainerConfig
import net.postchain.container.PostchainContainer

data class DockerPostchainContainer(val config: ContainerConfig, override val name: String): PostchainContainer
