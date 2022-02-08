package net.postchain.managedmode

import net.postchain.dapp.PostchainContainer
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
internal class ManagedModeExampleIT {
    private val imageName = DockerImageName.parse("chromaway/postchain-managed-mode:latest")
            .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")

    companion object {
        const val resourceFolder = "managed-mode-example"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)

    }

    private val node1 = PostchainContainer(imageName)
            .withNetwork(network)
            .withNetworkAliases("node1")
            .withClasspathResourceMapping("${resourceFolder}/node1", "${PostchainContainer.RELL_PATH}/config", BindMode.READ_ONLY)
            .withClasspathResourceMapping("${resourceFolder}/src", PostchainContainer.RELL_SRC, BindMode.READ_ONLY)
            .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
            .withFixedExposedPort(9871, 9871)

    @Test
    fun `Managed mode example`() {

    }

}