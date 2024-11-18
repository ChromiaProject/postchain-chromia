package net.postchain.images.common

import org.testcontainers.utility.DockerImageName

object DockerImages {

    fun postgresImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTGRES")
                ?: "postgres:14.14-alpine3.20@sha256:2bc30c8766a199d04c65abac9b4c08d0498cf96ebd256a02c31e8cc6ad95a4d6"

        return DockerImageName.parse(imageName)
    }

    fun chromiaServerImage(): DockerImageName {
        val imageName = System.getenv("POSTCHAIN_TEST_DOCKER_IMAGE_POSTCHAIN_SERVER")
                ?: "chromaway/chromia-server:latest"

        return DockerImageName.parse(imageName)
    }
}