package net.postchain.images.enterprise0

import assertk.assert
import assertk.assertions.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.postchain.client.core.PostchainClient
import net.postchain.common.hexStringToByteArray
import net.postchain.common.BlockchainRid
import net.postchain.dapp.*
import net.postchain.dapp.PostchainContainer.Companion.POSTCHAIN_PATH
import net.postchain.gtv.Gtv
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

/**
 * This test proves the functionality of enterprise0 dapp and the minimal needed configuration.
 *
 * We only use a single provider, which is configured in run.xml to be the initial provider and [adminPubKey].
 * This means that a single provider owns the entire network and is done to keep the voting steps simple.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Enterprise0ExampleIT {


    companion object {
        val consoleLogger = KotlinLogging.logger("TestLogger")
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
                .withEnv("POSTCHAIN_CLIENT_PRIVKEY", adminPrivKey) // Sign transactions via postchain-client with this key
                .withEnv("POSTCHAIN_CLIENT_PUBKEY", adminPubKey)   // Could also be added to properties file of this node
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

        // Here, adminPubKey is the same as we have in "initial_provider" module arg
        private val initialProviderPubKey = adminPubKey.hexStringToByteArray()

        @JvmStatic
        @BeforeAll
        fun setup() {
            consoleLogger.info { "Starting nodes..." }
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
        node1Db.awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider 1`() {
        node1.tx(0, "init")
        assert(node1.client(0).querySync("get_all_providers").asArray().size).isEqualTo(1)
    }

    @Test
    @Order(3)
    fun `Add node 1 to its own network`() {
        val provider = node1.client(0).query("get_provider", gtv("pubkey" to gtv(initialProviderPubKey))).get()

        consoleLogger.info { "Adding node 1 to its own network" }
        node1.tx(0, "add_node", provider, gtv(node1.pubKey.hexStringToByteArray()), gtv(node1.nodeHost), gtv(node1.nodePort.toLong()))
        node1Db.awaitNewBlock()
        assert(node1.client(0).query("is_node", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get().asBoolean()).isTrue()
        val nodeGtv = node1.client(0).query("get_node_data", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
        assert(nodeGtv.asDict()["active"]!!.asInteger()).isEqualTo(1L)
    }

    @Test
    @Order(4)
    fun `Make chain0 aware of itself`() {
        val provider = node1.client(0).query("get_provider", gtv("pubkey" to gtv(initialProviderPubKey))).get()
        val nodeGtv = node1.client(0).query("get_node", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
        val res = node1.execInContainer("sh", "propose_blockchain.sh", provider.asInteger().toString(), nodeGtv.asInteger().toString())
        consoleLogger.info { if (res.exitCode != 0) res.stderr else res.stdout }
        assert(res.stderr).isEmpty()
        val addChain0Proposal = node1.client(0).querySync("get_proposals_since", gtv("since" to gtv(0))).asArray().first()
        node1.tx(0, "make_vote", provider, addChain0Proposal.asDict()["rowid"]!!, gtv(true))

        assert(node1.client(0).querySync("get_all_blockchains").asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Make node 2 and 3 signers of c0`() {
        consoleLogger.info { "Adding node2 and node3 to node1" }
        val provider = node1.client(0).querySync("get_provider", gtv("pubkey" to gtv(initialProviderPubKey)))
        node1.tx(0, "add_node", provider, gtv(node2.pubKey.hexStringToByteArray()), gtv(node2.nodeHost), gtv(node2.nodePort.toLong()))
        node1Db.awaitNewBlock()
        node1.tx(0, "add_node", provider, gtv(node3.pubKey.hexStringToByteArray()), gtv(node3.nodeHost), gtv(node3.nodePort.toLong()))
        node1Db.awaitNewBlock()
        listOf(node2, node3).forEach { addedNode ->
            assert(node1.client(0).query("is_node", gtv("pubkey" to gtv(addedNode.pubKey.hexStringToByteArray()))).get().asBoolean(),
                    name = "Node ${addedNode.nodeHost} is added to ${node1.nodeHost}"
            ).isTrue()
        }
        val c0 = node1.client(0).querySync("get_blockchain", gtv("rid" to gtv(node1.getBlockchainRId(0))))
        val newSignerNodes = listOf(node2, node3).map { node ->
            node1.client(0).query("get_node", gtv("pubkey" to gtv(node.pubKey.hexStringToByteArray()))).get()
        }
        node1.tx(0, "propose_add_blockchain_signers", provider, c0, gtv(newSignerNodes))
        val addChain0Proposal = node1.client(0).querySync("get_proposals_since", gtv("since" to gtv(0L))).asArray().first()
        node1.tx(0, "make_vote", provider, addChain0Proposal.asDict()["rowid"]!!, gtv(true))
        // Adding signers will update the blockchain configuration after 5 blocks
        val heightWithSigners = node1Db.getHeight() + 5
        runAsync(node1Db, node2Db, node3Db) {
            it.awaitBlockHeight(heightWithSigners)
        }
        assert(node1.client(0).getBlockChainSigners(c0).size).isEqualTo(3)
        assert(node2.client(0).getBlockChainSigners(c0).size).isEqualTo(3)
        assert(node3.client(0).getBlockChainSigners(c0).size).isEqualTo(3)
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Deployment of new Dapp tests")
    inner class DappDeployment {

        val dappId = 100L
        lateinit var node2DappDb: ChainDatabaseCommunicator
        val dappToBrid = mutableMapOf<Long, BlockchainRid>()

        @BeforeAll
        fun `Deploy test-dapp to the network`() {
            listOf(node1, node2, node3).forEach { node ->
                Assumptions.assumeTrue {
                    node.client(0).querySync("get_all_blockchains", gtv(mapOf())).asArray().size == 1
                }
            }
            val applicationFolder = this::class.java.getResource("/$resourceFolder/dapp")!!
            val runConf = this::class.java.getResource("/$resourceFolder/dapp/run.xml")!!
            val rellConfig = RellRunConfigGenerator.generateCli(File(applicationFolder.toURI()), File(runConf.toURI()), R_LangVersion.of("0.10.8"), false).apply {
                RellRunConfigGenerator.buildFiles(this.config)
            }

            val nodeGtvs = node1.client(0).let { client ->
                listOf(node1, node2, node3).map {
                    consoleLogger.info { "Querying node id for ${it.nodeHost} on ${it.pubKey}" }
                    client.querySync("get_node", gtv("pubkey" to gtv(it.pubKey.hexStringToByteArray())))
                }
            }

            val provider = node2.client(0).querySync("get_provider", gtv("pubkey" to gtv(initialProviderPubKey)))
            rellConfig.config.chains.forEach { chain ->
                consoleLogger.info { "Adding test dapp ${chain.iid}" }
                chain.configs.forEach { (height, chainHeightConfig) ->
                    consoleLogger.info { "On height $height" }
                    node2.tx(0, "propose_blockchain", provider, gtv(GtvEncoder.encodeGtv(chainHeightConfig.gtvConfig)), gtv(nodeGtvs))
                    val addChain0Proposal = node2.client(0).querySync("get_proposals_since", gtv("since" to gtv(0L))).asArray().first()
                    val txId = node1.tx(0, "make_vote", provider, addChain0Proposal.asDict()["rowid"]!!, gtv(true))
                    dappToBrid[chain.iid] = node2.client(0)
                            .querySync("get_added_blockchain_rid", gtv("tx_rid" to gtv(txId.data)))
                            .asByteArray()
                            .let { BlockchainRid(it) }
                            .also { consoleLogger.info { "With blockchain ID ${it.toShortHex()}" } }
                }
            }
            val heightWithDappDeployed = node2Db.getHeight() + 1// Dapp is deployed and dapp db-table has been created
            runAsync(node1Db, node2Db, node3Db) {
                it.awaitBlockHeight(heightWithDappDeployed)
            }
            node2DappDb = postgres.createChainDatabaseCommunicator(dappId, node2.appConfig.databaseSchema)
                    .apply { awaitBlockHeight(0) }
        }

        @Test
        @Order(1)
        fun `Dapp is deployed`() {
            listOf(node1, node2, node3).forEach { node ->
                assert(node.client(0).querySync("get_all_blockchains", gtv(mapOf())).asArray().size).isEqualTo(2)
            }
        }

        @Test
        @Order(2)
        fun `Transactions can be sent to newly deployed dapp`() {
            Assumptions.assumeTrue(dappToBrid.containsKey(dappId))
            val testCity = "uppsala"
            node2.tx(dappToBrid[dappId]!!, "add_city", gtv(testCity))
            node2DappDb.awaitNewBlock()
            listOf(node1, node2, node3).forEach { node ->
                assert(node.client(dappToBrid[dappId]!!).query("get_cities", gtv(mapOf())).get().asArray().map { it.asString() })
                        .containsExactly(testCity)
            }
        }
    }

    private fun <T> runAsync(vararg obj: T, action: (T) -> Unit) {
        runBlocking {
            withContext(coroutineContext) {
                obj.asList().forEach {
                    launch { action(it) }
                }
            }
        }
    }
}

fun PostchainClient.getBlockChainSigners(blockChain: Gtv): Array<out Gtv> {
    return querySync("get_blockchain_signers", gtv("blockchain" to blockChain)).asArray()
}
