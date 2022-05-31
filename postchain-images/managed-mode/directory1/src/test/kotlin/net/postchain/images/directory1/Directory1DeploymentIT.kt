package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.*
import com.spotify.docker.client.DockerClient
import mu.KLogging
import mu.KotlinLogging
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.containers.bpm.DockerClientFactory
import net.postchain.dapp.*
import net.postchain.dapp.PostchainContainer.Companion.MOUNT_DIR
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.module.RellVersions
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
        private val node3Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node3")

        private val imageName = DockerImageName.parse("chromaway/postchain-directory1:latest")
                .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        private const val resourceFolder = "directory1-deployment"
        private val network: Network = Network.newNetwork()
        private lateinit var dapp1: Pair<Long, BlockchainRid>

        private val resolvedDockerHost = getResolvedDockerHost()
        private val dockerClient: DockerClient = DockerClientFactory.create()

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
                .withEnv("DEBUG", "true")
                .withEnv("POSTCHAIN_CLIENT_PRIVKEY", adminPrivKey) // Sign transactions via postchain-client with this key
                .withEnv("POSTCHAIN_CLIENT_PUBKEY", adminPubKey)   // Could also be added to properties file of this node
                .withLogConsumer(node1Logger)

        private val appConfig2 = parseConfig(this::class.java.getResource("/directory1-deployment/node2/node-config.properties")!!)
        private val node2 = PostchainContainer(imageName, appConfig2)
                .withNetwork(network)
                .withNetworkAliases("node2")
                .withClasspathResourceMapping("$resourceFolder/node2", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-directory1.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02B99A05912B01B7797D84D6660E9ED35FAEE078BD5BDF40026E0CC6E0CB2EF50C")
                .withEnv("NODE_HOST", "node2")
                .withEnv("NODE_PORT", "9872")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${POSTCHAIN_PATH}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withEnv("DEBUG", "true")
                .withLogConsumer(node2Logger)

        private val appConfig3 = setupMasterNodeConfig(
                this::class.java.getResource("/directory1-deployment/node3/node-config.properties")!!, resolvedDockerHost)
        private val node3 = PostchainContainer(imageName, appConfig3)
                .withNetwork(network)
                .withNetworkAliases("node3")
                .withClasspathResourceMapping("$resourceFolder/node3", "${POSTCHAIN_PATH}/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping("chain_zero/run-directory1.xml", "${POSTCHAIN_PATH}/chain_zero/manifest.xml", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withEnv("NODE_PUBKEY", "02839DDE1D2121CE72794E54180F5F5C3AD23543D419CB4C3640A854ACB1ADA9E6")
                .withEnv("NODE_HOST", "node3")
                .withEnv("NODE_PORT", "9873")
                .withEnv("BOOTSTRAP_NODE_PUBKEY", "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
                .withEnv("BOOTSTRAP_NODE_HOST", "node1")
                .withEnv("BOOTSTRAP_NODE_PORT", "9871")
                .withEnv("RELL_OUT", "${MOUNT_DIR}/chain0-generated")
                .withEnv("WIPE_DB", "true")
                .withEnv("DEBUG", "true")
                .withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                .withMasterDockerConfig()
                .withLogConsumer(node3Logger)

        private lateinit var node1Db: ChainDatabaseCommunicator

        @JvmStatic
        @BeforeAll
        fun setup() {
            consoleLogger.info { "Starting nodes..." }
            removeSubnodeContainers()
            startContainers(node1, node2, node3)

            node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopContainers(node1, node2, node3)
            removeSubnodeContainers()
        }

        private fun removeSubnodeContainers() {
            dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).forEach {
                if (it.image().contains("postchain-subnode")) {
                    dockerClient.stopContainer(it.id(), 0)
                    dockerClient.removeContainer(it.id())
                }
            }
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
    fun `Initialize network with provider1`() {
        node1.txAsAdmin(0, "init")
        node1.client(0).querySync("get_all_providers").also {
            assert(it.asArray().size).isEqualTo(1)
        }
    }

    @Test
    @Order(3)
    fun `Add node1 to its own network`() {
        consoleLogger.info("Adding node1 to its own network")

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
        val container = node1.client(0).getSystemContainer()
        val res = node1.execInContainer("sh",
                "propose_blockchain.sh", provider.asInteger().toString(), container.asInteger().toString())
        consoleLogger.info { if (res.exitCode != 0) res.stderr else "chain0 has been proposed" }
        assert(res.stderr).isEmpty()

        node1Db.awaitNewBlock()
        assert(node1.getAllBlockchains().asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Add node2 as signer to c0`() {
        consoleLogger.info("Adding node2 to the cluster")
        val brid0 = node1.getBlockchainRid(0)
        val provider1 = node1.client(0).getProvider1()
        val cluster = node1.client(0).getSystemCluster()

        consoleLogger.info("Registering provider2")
        val provider2 = Context(node1, node1Db, provider1)
                .registerNodeAsProvider(cluster, node2)

        consoleLogger.info("Adding node2 to [node1] network")
        addNode(node2, provider2, cluster, brid0, node1)

        // Asserting that node2 is signers of chain0
        val c0 = node1.getBlockchainGtv(brid0)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(c0).size).isEqualTo(2)
            assert(node2.getBlockchainSigners(c0).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        consoleLogger.info("Adding node3 to the cluster")
        val brid0 = node1.getBlockchainRid(0)
        val provider1 = node1.client(0).getProvider1()
        val provider2 = node2.getProvider()
        val cluster = node1.client(0).getSystemCluster()

        consoleLogger.info("Registering provider3")
        val provider3 = Context(node1, node1Db, provider1, approverNode = node2, approver = provider2)
                .registerNodeAsProvider(cluster, node3)

        consoleLogger.info("Adding node3 to [node1, node2] network")
        addNode(node3, provider3, cluster, brid0, node1)

        // Asserting that node2 is signers of chain0
        val c0 = node1.getBlockchainGtv(brid0)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(c0).size).isEqualTo(3)
            assert(node2.getBlockchainSigners(c0).size).isEqualTo(3)
            assert(node3.getBlockchainSigners(c0).size).isEqualTo(3)
        }
    }

    @Test
    @Order(7)
    fun `Deploy new dapp`() {
        listOf(node1, node2, node3).forEach { node ->
            Assumptions.assumeTrue {
                node.getAllBlockchains().asArray().size == 1
            }
        }

        val applicationFolder = this::class.java.getResource("/$resourceFolder/dapp")!!
        val runConf = this::class.java.getResource("/$resourceFolder/dapp/run.xml")!!
        val rellConfig = RellRunConfigGenerator.generateCli(File(applicationFolder.toURI()), File(runConf.toURI()), RellVersions.VERSION, false).apply {
            RellRunConfigGenerator.buildFiles(this.config)
        }

        val provider1 = node1.client(0).getProvider1()
        val provider2 = node2.getProvider()
        val provider3 = node3.getProvider()
        val container = node1.client(0).getSystemContainer()
        rellConfig.config.chains.forEach { chain ->
            consoleLogger.info { "Adding test dapp ${chain.iid}" }
            chain.configs.forEach { (height, config) ->
                consoleLogger.info { "Proposing a blockchain on height $height" }
                dapp1 = 100L to GtvToBlockchainRidFactory.calculateBlockchainRid(config.gtvConfig)
                val configGtv = gtv(GtvEncoder.encodeGtv(config.gtvConfig))
                node3.tx(0, "propose_blockchain", provider3, configGtv, container)

                // Voting
                val p1 = node1.approveProposal(provider1)
                consoleLogger.info { "node1 voted for proposal: $p1" }

                val p2 = node2.approveProposal(provider2)
                consoleLogger.info { "node2 voted for proposal: $p2" }
            }
        }

        // Asserting that blockchain is added
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                assert(node.getAllBlockchains().asArray().size).isEqualTo(2)
            }
        }

        // Asserting that node1/node2/node3 are signers of newly added blockchain
        val c100 = node1.getBlockchainGtv(dapp1.second)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(c100).size).isEqualTo(3)
            assert(node2.getBlockchainSigners(c100).size).isEqualTo(3)
            assert(node3.getBlockchainSigners(c100).size).isEqualTo(3)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            assert(all.filter { it.image().contains("postchain-subnode") && it.state() == "running" }.size).isEqualTo(1)
        }
    }

    @Test
    @Order(9)
    fun `Transactions can be sent to newly deployed dapp`() {
        val city = "Heraklion"
        node2.tx(dapp1.second, "add_city", gtv(city))
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val cities = awaitQueryResult { node.client(dapp1.second).querySync("get_cities") }!!
                        .asArray().map { it.asString() }
                assert(cities).containsExactly(city)
            }
        }
    }

}
