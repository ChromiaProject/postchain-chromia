package net.postchain.container

interface PostchainContainerClientFactory {
    fun create(): PostchainContainerClient
}
