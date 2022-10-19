package net.postchain.images.common

import org.testcontainers.utility.DockerImageName

object DockerImages {

    fun postgresImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTGRES")
                ?: "registry.gitlab.com/chromaway/postchain-distribution/chromaway/postgres:${versionTag()}"

        return DockerImageName.parse(imageName)
    }

    fun postchainServerImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTCHAIN_SERVER")
                ?: "registry.gitlab.com/chromaway/postchain-distribution/chromaway/postchain-server:${versionTag()}"

        return DockerImageName
                .parse(imageName)
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
    }

    private fun versionTag(): String {
        return javaClass.getPackage()?.implementationVersion ?: "latest"
    }
}