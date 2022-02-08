package net.postchain.managedmode

import assertk.assert
import assertk.assertions.isZero
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
internal class ManagedModeExampleIT {

    companion object {
        private val imageName = DockerImageName.parse("chromaway/postchain-managed-mode:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        const val resourceFolder = "managed-mode-example"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)


        private val node1 = PostchainContainer(imageName)
                .withNetwork(network)
                .withNetworkAliases("node1")
                .withClasspathResourceMapping("${resourceFolder}/node1", "${PostchainContainer.POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withFixedExposedPort(9871, 9871)

        @JvmStatic
        @BeforeAll
        fun setup() {
            startContainers(node1)
        }


        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopContainers(node1)
        }
    }

    @Test
    fun `Chain0 dapp is deployed`() {
        assert(
                node1.execInContainer("ls", "/opt/chromaway/postchain/chain_zero/chain_zero.rell").exitCode
        ).isZero()
    }

    @Test
    fun `Managed mode example`() {

    }

}