package net.postchain.container

interface PostchainContainerClient: AutoCloseable {

    fun createContainer(config: PostchainContainerConfig): PostchainContainer
    fun removeContainer(name: String): Boolean

    fun startContainer(name: String): Boolean
    fun stopContainer(name: String): Boolean

    fun listContainers(): List<String>
    fun findContainer(name: String): Boolean

    fun findImage(imageName: String): Boolean
    fun pull(imageName: String)
}
