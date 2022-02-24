package net.postchain.managedmode

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import net.postchain.common.hexStringToByteArray
import net.postchain.dapp.*
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.*
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ManagedModeExampleIT {

    companion object {
        private val imageName = DockerImageName.parse("chromaway/postchain-managed-mode:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        const val resourceFolder = "managed-mode-example"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)


        private val node1 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/managed-mode-example/node1/node-config.properties")!!.file))
                .withNetwork(network)
                .withNetworkAliases("node1")
                .withClasspathResourceMapping("$resourceFolder/node1", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("NODE_HOST", "node1")
                .withEnv("NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withFixedExposedPort(9871, 9871)
                .withEnv("JAVA_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005")

        private val node2 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/managed-mode-example/node2/node-config.properties")!!.file))
                .withNetwork(network)
                .withNetworkAliases("node2")
                .withClasspathResourceMapping("$resourceFolder/node2", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02B99A05912B01B7797D84D6660E9ED35FAEE078BD5BDF40026E0CC6E0CB2EF50C")
                .withEnv("NODE_HOST", "node2")
                .withEnv("NODE_PORT", "9872")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withFixedExposedPort(9872, 9872)

        private val node3 = PostchainContainer(imageName, parseConfig(this::class.java.getResource("/managed-mode-example/node3/node-config.properties")!!.file))
                .withNetwork(network)
                .withNetworkAliases("node3")
                .withClasspathResourceMapping("$resourceFolder/node3", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02839DDE1D2121CE72794E54180F5F5C3AD23543D419CB4C3640A854ACB1ADA9E6")
                .withEnv("NODE_HOST", "node3")
                .withEnv("NODE_PORT", "9873")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withFixedExposedPort(9873, 9873)

        @JvmStatic
        @BeforeAll
        fun setup() {
            println("Starting nodes...")
            startContainers(node1, node2, node3)
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

    @Test
    @Order(2)
    fun `Add node to the network`() {
        val db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
        println("Registering provider")
        node1.tx(0, "register_provider", gtv(adminPubKey.hexStringToByteArray()))
        db.awaitNewBlock()
        println("done")

        val provider = node1.client(0).query("get_provider", gtv("pubkey" to gtv(adminPubKey.hexStringToByteArray()))).get()
        println("Enabling provider")
        node1.tx(0, "enable_provider", provider)
        db.awaitNewBlock()

        println("Adding node")
        node1.tx(0, "add_node", provider, gtv(node1.pubKey.hexStringToByteArray()), gtv("node1"), gtv(9871))
        db.awaitNewBlock()
        val nodeGtv = node1.client(0).query("get_node_data", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
        assert(nodeGtv.asDict()["active"]!!.asInteger()).isEqualTo(1)
    }
}