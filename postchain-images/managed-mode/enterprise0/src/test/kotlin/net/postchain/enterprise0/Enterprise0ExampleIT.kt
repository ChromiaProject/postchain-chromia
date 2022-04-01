package net.postchain.enterprise0

import assertk.assert
import assertk.assertions.isZero
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.dapp.parseConfig
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Enterprise0ExampleIT {


    companion object {
        private val logger = KotlinLogging.logger {}

        private val node1Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node1")
        private val node2Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node2")
        private val node3Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node3")

        private val imageName = DockerImageName.parse("chromaway/postchain-enterprise0:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        private const val resourceFolder = "enterprise0-example"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)

        private val node1 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/enterprise0-example/node1/node-config.properties")!!))
                .withNetwork(network)
                .withNetworkAliases("node1")
                .withClasspathResourceMapping("$resourceFolder/node1", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-enterprise0.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("NODE_HOST", "node1")
                .withEnv("NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node1Logger)

        private val node2 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/enterprise0-example/node2/node-config.properties")!!))
                .withNetwork(network)
                .withNetworkAliases("node2")
                .withClasspathResourceMapping("$resourceFolder/node2", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-enterprise0.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02B99A05912B01B7797D84D6660E9ED35FAEE078BD5BDF40026E0CC6E0CB2EF50C")
                .withEnv("NODE_HOST", "node2")
                .withEnv("NODE_PORT", "9872")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node2Logger)

        private val node3 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/enterprise0-example/node3/node-config.properties")!!))
                .withNetwork(network)
                .withNetworkAliases("node3")
                .withClasspathResourceMapping("$resourceFolder/node3", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-enterprise0.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02839DDE1D2121CE72794E54180F5F5C3AD23543D419CB4C3640A854ACB1ADA9E6")
                .withEnv("NODE_HOST", "node3")
                .withEnv("NODE_PORT", "9873")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node3Logger)

        private lateinit var node1Db: ChainDatabaseCommunicator
        private lateinit var node2Db: ChainDatabaseCommunicator
        private lateinit var node3Db: ChainDatabaseCommunicator

        @JvmStatic
        @BeforeAll
        fun setup() {
            println("Starting nodes...")
            startContainers(node1, node2, node3)
            node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
            node2Db = postgres.createChainDatabaseCommunicator(0, node2.appConfig.databaseSchema)
            node3Db = postgres.createChainDatabaseCommunicator(0, node3.appConfig.databaseSchema)
        }


        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopContainers(node1, node2, node3)
        }
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        assert(
                node1.execInContainer("ls", "/opt/chromaway/postchain/chain0-generated/blockchains/0").exitCode
        ).isZero()
    }


    fun <T> runAsync(vararg obj: T, action: (T) -> Unit) {
        runBlocking {
            withContext(coroutineContext) {
                obj.asList().forEach {
                    launch { action(it) }
                }
            }
        }
    }
}
