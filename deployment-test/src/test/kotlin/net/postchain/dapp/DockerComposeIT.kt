package net.postchain.dapp

import assertk.assert
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

internal class PostchainComposeContainer(vararg composeFiles: File) : DockerComposeContainer<PostchainComposeContainer>(*composeFiles)

@Testcontainers
class DockerComposeIT {

    @Container
    private val composeContainer = PostchainComposeContainer(File(this::class.java.getResource("/docker-compose.yml")!!.toURI()))
            .withEnv("DAPP_PATH", this.javaClass.getResource("/simple-dapp")!!.path)
            .withEnv("IMAGE", System.getProperty("POSTCHAIN_TEST_DAPP_IMAGE", "chromaway/chromia-server:latest"))

    @Test
    fun `Postchain can be started with docker compose file`() {

        composeContainer.getContainerByServiceName("postchain_1").let { postchain ->
            assert(postchain.isPresent).isTrue()
            assert(postchain.get().isRunning)
        }
        composeContainer.getContainerByServiceName("postgres_1").let { postgres ->
            assert(postgres.isPresent).isTrue()
            assert(postgres.get().isRunning)
        }

    }
}
