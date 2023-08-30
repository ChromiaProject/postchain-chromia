package net.postchain.images.common

import org.testcontainers.utility.DockerImageName

object DockerImages {

    fun postgresImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTGRES")
                ?: "postgres:14.9-alpine3.18"

        return DockerImageName.parse(imageName)
    }

    fun chromiaServerImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTCHAIN_SERVER")
                ?: "chromaway/chromia-server:latest"

        return DockerImageName.parse(imageName)
    }
}