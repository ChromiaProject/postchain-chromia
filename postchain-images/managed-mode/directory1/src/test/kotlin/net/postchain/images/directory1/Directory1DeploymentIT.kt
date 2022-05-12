package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import mu.KLogging
import mu.KotlinLogging
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.dapp.parseConfig
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.*
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Directory1DeploymentIT {

    companion object : KLogging() {
        val consoleLogger = KotlinLogging.logger("TestLogger")

        private val node1Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node1")

        private val imageName = DockerImageName.parse("chromaway/postchain-directory1:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        private const val resourceFolder = "directory1-deployment"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)

        private val appConfig1 = parseConfig(this::class.java.getResource("/directory1-deployment/node1/node-config.properties")!!)
        private val node1 = PostchainContainer(imageName, appConfig1)
                .withNetwork(network)
                .withNetworkAliases("node1")
                .withClasspathResourceMapping("$resourceFolder/node1", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-directory1.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("NODE_HOST", "node1")
                .withEnv("NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node1Logger)

        private lateinit var node1Db: ChainDatabaseCommunicator

        @JvmStatic
        @BeforeAll
        fun setup() {
            consoleLogger.info { "Starting nodes..." }
            startContainers(node1)
            node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopContainers(node1)
        }
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        assert(
                node1.execInContainer("ls", "/opt/chromaway/postchain/chain0-generated/blockchains/0").exitCode
        ).isZero()
        node1Db.awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider 1`() {
        node1.tx(0, "init")
        node1.client(0).querySync("get_all_providers").also {
            assert(it.asArray().size).isEqualTo(1)
        }
    }
}