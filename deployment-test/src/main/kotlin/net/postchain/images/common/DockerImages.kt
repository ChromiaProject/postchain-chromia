package net.postchain.images.common

import org.testcontainers.utility.DockerImageName

object DockerImages {

    fun postgresImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTGRES")
                ?: "postgres:16.6-alpine3.21@sha256:aba1fab94626cf8b0f4549055214239a37e0a690f03f142b7bca05b9ed36c6db"

        return DockerImageName.parse(imageName)
    }

    fun chromiaServerImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTCHAIN_SERVER")
                ?: "chromaway/chromia-server:latest"

        return DockerImageName.parse(imageName)
    }
}