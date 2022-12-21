package net.postchain.test.dapp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

internal class PostchainComposeContainer(vararg composeFiles: File) : DockerComposeContainer<PostchainComposeContainer>(*composeFiles)

@Testcontainers
class RellTestIT {

    private lateinit var composeContainer: PostchainComposeContainer

    @BeforeEach
    fun setup() {
        composeContainer = PostchainComposeContainer(File(this::class.java.getResource("/docker-compose.yml")!!.toURI()))
                .withEnv("DAPP_PATH", this.javaClass.getResource("/rell-tests")!!.path)
    }

    @AfterEach
    fun breakdown() {
        composeContainer.stop()
    }

    @Test
    fun `Rell test should fail and stop container`() {
        composeContainer
                .withEnv(mapOf(
                        "IMAGE" to System.getProperty("POSTCHAIN_TEST_DAPP_IMAGE", "chromaway/postchain-test-dapp:latest"),
                        "COMMAND" to "test",
                        "CODEGEN" to "false"
                ))
                .waitingFor("postchain_1", Wait.forLogMessage(".*Running tests.*\\s", 1))
                .start()
    }

    @Test
    fun `Possible to start node as usual`() {
        composeContainer
                .withEnv(mapOf(
                        "IMAGE" to System.getProperty("POSTCHAIN_TEST_DAPP_IMAGE", "chromaway/postchain-test-dapp:latest"),
                        "COMMAND" to "run-node-auto",
                        "CODEGEN" to "true"
                ))
                .waitingFor("postchain_1", Wait.forLogMessage(".*Postchain node is running.*\\s", 1))
                .start()
    }
}
