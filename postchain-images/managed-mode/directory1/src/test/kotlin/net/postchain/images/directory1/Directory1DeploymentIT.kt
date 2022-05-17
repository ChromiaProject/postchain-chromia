package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.isZero
import mu.KLogging
import mu.KotlinLogging
import net.postchain.base.BlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.dapp.*
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.model.R_LangVersion
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import org.junit.jupiter.api.*
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File

internal val initialProviderPubKey = adminPubKey.hexStringToByteArray()

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Directory1DeploymentIT {

    companion object : KLogging() {
        val consoleLogger = KotlinLogging.logger("TestLogger")

        private val node1Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node1")
        private val node2Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node2")

        private val imageName = DockerImageName.parse("chromaway/postchain-directory1:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        private const val resourceFolder = "directory1-deployment"
        private val network: Network = Network.newNetwork()
        private lateinit var dapp1: Pair<Long, BlockchainRid>

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
//                .withEnv("POSTCHAIN_DB_URL", "jdbc:postgresql://172.23.32.1:5432/postchain")
                .withEnv("NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("NODE_HOST", "node1")
                .withEnv("NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node1Logger)

        private val appConfig2 = parseConfig(this::class.java.getResource("/directory1-deployment/node2/node-config.properties")!!)
        private val node2 = PostchainContainer(imageName, appConfig2)
                .withNetwork(network)
                .withNetworkAliases("node2")
                .withClasspathResourceMapping("$resourceFolder/node2", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-directory1.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
//                .withEnv("POSTCHAIN_DB_URL", "jdbc:postgresql://172.23.32.1:5432/postchain")
                .withEnv("NODE_PUBKEY", "02B99A05912B01B7797D84D6660E9ED35FAEE078BD5BDF40026E0CC6E0CB2EF50C")
                .withEnv("NODE_HOST", "node2")
                .withEnv("NODE_PORT", "9872")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withLogConsumer(node2Logger)

        private lateinit var node1Db: ChainDatabaseCommunicator

        @JvmStatic
        @BeforeAll
        fun setup() {
            consoleLogger.info { "Starting nodes..." }
            startContainers(node1, node2)

            node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
            /*
            val localDbConfig = DatabaseConfig(
                    "org.postgresql.Driver",
                    "jdbc:postgresql://localhost:5432/postchain",
                    "postchain",
                    "postchain"
            )
            node1Db = ChainDatabaseCommunicator(0, node1.appConfig.databaseSchema, localDbConfig)
             */
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopContainers(node1, node2)
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
        node1.txAsAdmin(0, "init")
        node1.client(0).querySync("get_all_providers").also {
            assert(it.asArray().size).isEqualTo(1)
        }
    }

    @Test
    @Order(3)
    fun `Add node 1 to its own network`() {
        consoleLogger.info("Adding node 1 to its own network")

        val provider = node1.client(0).getProvider1()
        val cluster = node1.client(0).getSystemCluster()

        node1.txAsAdmin(0, "add_node",
                provider,
                gtv(node1.pubKeyByteArray),
                gtv(node1.nodeHost), gtv(node1.nodePort.toLong()),
                cluster
        )
        node1Db.awaitNewBlock()
        node1.client(0).query(
                "is_node", gtv("pubkey" to gtv(node1.pubKeyByteArray))
        ).also {
            assert(it.get().asBoolean()).isTrue()
        }

        node1.client(0).query(
                "get_node_data", gtv("pubkey" to gtv(node1.pubKeyByteArray))
        ).also {
            assert(it.get().asDict()["active"]!!.asInteger()).isEqualTo(1L)
        }
    }

    @Test
    @Order(4)
    fun `Make chain0 aware of itself`() {
        val provider = node1.client(0).getProvider1()
        node1.proposeChain0(provider)
        node1Db.awaitNewBlock()
        node1.client(0).querySync(
                "get_blockchains", gtv("include_inactive" to gtv(true))
        ).also {
            assert(it.asArray().size).isEqualTo(1)
        }
    }

    @Test
    @Order(5)
    fun `Make node 2 signers of c0`() {
        consoleLogger.info("Adding node 2 to node 1")
        val brid0 = node1.getBlockchainRid(0)
        val provider1 = node1.client(0).getProvider1()
        val cluster = node1.client(0).getSystemCluster()

        consoleLogger.info("Registering provider2")
        val provider2 = Context(node1, node1Db, provider1)
                .registerNodeAsProvider(cluster, node2)

        consoleLogger.info("Adding node2 to [node1] network")
        addNode(node2, provider2, cluster, brid0, node1)

        // Asserting that node2 is signers of chain0
        val c0 = awaitQueryResult {
            node1.client(0).querySync("get_blockchain", gtv("rid" to gtv(brid0.data)))
        }!!
        awaitUntilAsserted {
            assert(node1.client(0).getBlockchainSigners(c0).size).isEqualTo(2)
            assert(node2.client(0).getBlockchainSigners(c0).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Deploy new dapp`() {
        listOf(node1, node2).forEach { node ->
            Assumptions.assumeTrue {
                node.client(0).getAllBlockchains().asArray().size == 1
            }
        }

        val applicationFolder = this::class.java.getResource("/$resourceFolder/dapp")!!
        val runConf = this::class.java.getResource("/$resourceFolder/dapp/run.xml")!!
        val rellConfig = RellRunConfigGenerator.generateCli(File(applicationFolder.toURI()), File(runConf.toURI()), R_LangVersion.of("0.10.8"), false).apply {
            RellRunConfigGenerator.buildFiles(this.config)
        }

        val provider1 = node1.client(0).getProvider1()
        val provider2 = node2.getProvider()
        val container = node1.client(0).getSystemContainer()
        rellConfig.config.chains.forEach { chain ->
            consoleLogger.info { "Adding test dapp ${chain.iid}" }
            chain.configs.forEach { (height, config) ->
                consoleLogger.info { "Proposing a blockchain on height $height" }
                dapp1 = 100L to BlockchainRidFactory.calculateBlockchainRid(config.gtvConfig)
                val configGtv = gtv(GtvEncoder.encodeGtv(config.gtvConfig))
                node2.tx(0, "propose_blockchain", provider2, configGtv, container)

                val proposal = awaitQueryResult { node1.client(0).getProposal() }
                consoleLogger.info { "Making a vote for proposal: $proposal" }
                node1.txAsAdmin(0, "make_vote", provider1, proposal!!, gtv(true))
            }
        }

        awaitUntilAsserted {
            listOf(node1, node2).forEach { node ->
                assert(node.client(0).getAllBlockchains().asArray().size).isEqualTo(2)
            }
        }
    }

    @Test
    @Order(7)
    fun `Transactions can be sent to newly deployed dapp`() {
        val city = "Heraklion"
        node2.tx(dapp1.second, "add_city", gtv(city))
        awaitUntilAsserted {
            listOf(node1, node2).forEach { node ->
                val cities = awaitQueryResult {
                    node.client(dapp1.second).querySync("get_cities")
                }!!.asArray().map { it.asString() }
                assert(cities).containsExactly(city)
            }
        }
    }

}
