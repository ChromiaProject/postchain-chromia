package net.postchain.chromia0

import assertk.assert
import assertk.assertions.*
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.dapp.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.server.service.AddPeerRequest
import net.postchain.server.service.InitializeBlockchainRequest
import net.postchain.server.service.PeerServiceGrpc
import net.postchain.server.service.PostchainServiceGrpc
import org.junit.jupiter.api.*
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File

/**
 * This test proves the functionality of chromia0 dapp and the minimal needed configuration.
 *
 * The admin adds itself as the only provider for the network. The admin is the only one who can add providers,
 * and providers can do a subset of all chain0 operations. This is done to make the test simpler.
 *
 * NOTE:
 * node1 & node2 runs regular managed mode
 * node3 runs as a master node
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Chromia0ExampleIT {

    companion object {
        val consoleLogger = KotlinLogging.logger("TestLogger")
        private val logger = KotlinLogging.logger {}

        private val node1Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node1")
        private val node2Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node2")
        private val node3Logger = Slf4jLogConsumer(logger.underlyingLogger).withMdc("node", "node3")

        private val imageName = DockerImageName.parse("chromaway/postchain-server:latest")
            .asCompatibleSubstituteFor("chromaway/postchain-dapp:latest")
        private const val resourceFolder = "chromia0-example"
        private val network: Network = Network.newNetwork()

        @Container
        private val postgres = ChromaWayPostgresContainer()
            .withNetwork(network)

        private val node1 = postchainServer("node1", node1Logger, 7740)
        private val node2 = postchainServer("node2", node2Logger, 7741)
        private val node3 = postchainServer("node3", node3Logger, 7742)

        private fun postchainServer(hostName: String, logConsumer: Slf4jLogConsumer?, apiPort: Int) =
            PostchainContainer(
                imageName,
                parseConfig(this::class.java.getResource("/chromia0-example/$hostName/node-config.properties")!!),
                startupMsg = "Server started, listening on 50051"
            )
                .withNetwork(network)
                .withNetworkAliases(hostName)
                .withExposedPorts(50051, apiPort)
                .withClasspathResourceMapping("$resourceFolder/$hostName", "/config", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withLogConsumer(logConsumer)

        private lateinit var node1Db: ChainDatabaseCommunicator
        private lateinit var node2Db: ChainDatabaseCommunicator
        private lateinit var node3Db: ChainDatabaseCommunicator

        private lateinit var channel1: ManagedChannel
        private lateinit var channel2: ManagedChannel
        private lateinit var channel3: ManagedChannel

        private lateinit var chain0Config: File
        private lateinit var brid: BlockchainRid

        init {
            createChain0Config()
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            consoleLogger.info { "Starting nodes..." }
            startContainers(node1, node2, node3)

            channel1 = createChannel(node1).usePlaintext().build()
            channel2 = createChannel(node2).usePlaintext().build()
            channel3 = createChannel(node3).usePlaintext().build()
            addPeer(channel1, node1)
            addPeer(channel2, node2)
            addPeer(channel2, node1)
            addPeer(channel3, node3)
            addPeer(channel3, node1)
            brid = startBlockchain(channel1, chain0Config).let { BlockchainRid.buildFromHex(it) }
            startBlockchain(channel2, chain0Config)
            startBlockchain(channel3, chain0Config)

            node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)
            node2Db = postgres.createChainDatabaseCommunicator(0, node2.appConfig.databaseSchema)
            node3Db = postgres.createChainDatabaseCommunicator(0, node3.appConfig.databaseSchema)
        }

        private fun createChannel(target: PostchainContainer) =
            ManagedChannelBuilder.forTarget("${target.host}:${target.getMappedPort(50051)}")

        private fun addPeer(channel: ManagedChannel, peer: PostchainContainer) {
            val service = PeerServiceGrpc.newBlockingStub(channel)
            service.addPeer(
                AddPeerRequest.newBuilder()
                    .setHost(peer.nodeHost)
                    .setPort(peer.nodePort)
                    .setPubkey(peer.pubKey)
                    .build()
            )
        }

        private fun startBlockchain(channel: ManagedChannel, config: File): String {
            return PostchainServiceGrpc.newBlockingStub(channel)
                .initializeBlockchain(
                    InitializeBlockchainRequest.newBuilder()
                        .setChainId(0)
                        .setGtv(ByteString.copyFrom(config.readBytes()))
                        .build()
                ).brid
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            channel1.shutdownNow()
            channel2.shutdownNow()
            channel3.shutdownNow()
            stopContainers(node1, node2, node3)
        }

        private fun createChain0Config() {
            val applicationFolder = this::class.java.getResource("/chromia0/rell")!!
            val runConf = this::class.java.getResource("/chain_zero/run-chromia0.xml")!!
            val configFiles = RellRunConfigGenerator.generateCli(
                File(applicationFolder.toURI()),
                File(runConf.toURI()),
                RellVersions.VERSION,
                false
            ).let {
                RellRunConfigGenerator.buildFiles(it.config)
            }
            val gtvFile = kotlin.io.path.createTempFile(suffix = ".gtv")
            configFiles["blockchains/0/0.gtv"]!!.write(gtvFile.toFile())
            chain0Config = gtvFile.toFile()
        }
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        node1Db.awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Make admin a provider for the network`() {
        println("Registering provider")
        node1.txAsAdmin(brid, "register_provider", gtv(adminPubKey.hexStringToByteArray()))

        val provider =
            node1.client(brid).querySync("get_provider", gtv("pubkey" to gtv(adminPubKey.hexStringToByteArray())))
        println("Enabling provider")
        node1.txAsAdmin(brid, "enable_provider", provider)
        node1.client(brid).querySync("get_provider_data", gtv("pubkey" to gtv(adminPubKey.hexStringToByteArray())))
            .asDict().let {
                assert(
                    it["active"]!!.asBoolean(),
                    name = "Provider is activated"
                )
                    .isTrue()
            }
    }

    @Test
    @Order(3)
    fun `Add node to the network`() {
        println("Adding node 1 to its own network")
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(adminPubKey.hexStringToByteArray()),
            gtv(node1.pubKey.hexStringToByteArray()),
            gtv(node1.nodeHost),
            gtv(node1.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        assert(
            node1.client(brid).querySync("is_node", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray())))
                .asBoolean()
        ).isTrue()
        val nodeGtv =
            node1.client(brid).querySync("get_node_data", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray())))
        assert(nodeGtv.asDict()["active"]!!.asInteger()).isEqualTo(1L)
    }

    @Test
    @Order(4)
    fun `Make chain0 aware of itself`() {
        val nodeGtv = node1.client(brid).query("get_node", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
        node1.txAsAdmin(brid, "add_blockchain", gtv(chain0Config.readBytes()), gtv(listOf(nodeGtv)))
        assert(node1.client(brid).querySync("get_all_blockchains", gtv(mapOf())).asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Make node 2 and 3 signers of c0`() {
        println("Adding nodes 2 and 3 to node 1")
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(adminPubKey.hexStringToByteArray()),
            gtv(node2.pubKey.hexStringToByteArray()),
            gtv(node2.nodeHost),
            gtv(node2.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(adminPubKey.hexStringToByteArray()),
            gtv(node3.pubKey.hexStringToByteArray()),
            gtv(node3.nodeHost),
            gtv(node3.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        listOf(node2, node3).forEach { addedNode ->
            assert(
                node1.client(brid).querySync("is_node", gtv("pubkey" to gtv(addedNode.pubKey.hexStringToByteArray())))
                    .asBoolean(),
                name = "Node ${addedNode.nodeHost} is added to ${node1.nodeHost}"
            ).isTrue()
        }

        println("Adding node 2 and 3 as signers of c0")
        node1.client(brid).also { client ->
            val newSignerNodes = listOf(node2, node3).map { node ->
                client.querySync("get_node", gtv("pubkey" to gtv(node.pubKey.hexStringToByteArray())))
            }
            val c0 = client.querySync("get_blockchain", gtv("rid" to gtv(brid.toHex())))
            node1.txAsAdmin(brid, "add_blockchain_signers", c0, gtv(newSignerNodes))
            // Adding signers will update the blockchain configuration after 5 blocks
            val heightWithSigners = node1Db.getHeight() + 5
            runAsync(node1Db, node2Db, node3Db) {
                it.awaitBlockHeight(heightWithSigners)
            }
            assert(client.getBlockChainSigners(c0).size).isEqualTo(3)
            assert(node2.client(brid).getBlockChainSigners(c0).size).isEqualTo(3)
            assert(node3.client(brid).getBlockChainSigners(c0).size).isEqualTo(3)
        }
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
                    node.client(brid).querySync("get_all_blockchains", gtv(mapOf())).asArray().size == 1
                }
            }
            val applicationFolder = this::class.java.getResource("/$resourceFolder/dapp")!!
            val runConf = this::class.java.getResource("/$resourceFolder/dapp/run.xml")!!
            val rellConfig = RellRunConfigGenerator.generateCli(
                File(applicationFolder.toURI()),
                File(runConf.toURI()),
                RellVersions.VERSION,
                false
            ).apply {
                RellRunConfigGenerator.buildFiles(this.config)
            }

            val nodeGtvs = node1.client(brid).let { client ->
                listOf(node1, node2, node3).map {
                    println("Querying node id for ${it.nodeHost} on ${it.pubKey}")
                    client.querySync("get_node", gtv("pubkey" to gtv(it.pubKey.hexStringToByteArray())))
                }
            }

            rellConfig.config.chains.forEach { chain ->
                println("Adding test dapp ${chain.iid}")
                chain.configs.forEach { (height, chainHeightConfig) ->
                    println("On height $height")
                    val txId = node2.txAsAdmin(
                        brid,
                        "add_blockchain",
                        gtv(GtvEncoder.encodeGtv(chainHeightConfig.gtvConfig)),
                        gtv(nodeGtvs)
                    )
                    dappToBrid[chain.iid] = node2.client(brid)
                        .querySync("get_added_blockchain_rid", gtv("tx_rid" to gtv(txId.data)))
                        .asByteArray()
                        .let { BlockchainRid(it) }
                        .also { println("With blockchain ID ${it.toShortHex()}") }
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
        @Order(6)
        fun `Dapp is deployed`() {
            listOf(node1, node2, node3).forEach { node ->
                assert(node.client(brid).querySync("get_all_blockchains", gtv(mapOf())).asArray().size).isEqualTo(2)
            }
        }

        @Test
        @Order(7)
        fun `Transactions can be sent to newly deployed dapp`() {
            Assumptions.assumeTrue(dappToBrid.containsKey(dappId))
            val testCity = "uppsala"
            node2.txAsAdmin(dappToBrid[dappId]!!, "add_city", gtv(testCity))
            listOf(node1, node2, node3).forEach { node ->
                assert(
                    node.client(dappToBrid[dappId]!!).querySync("get_cities", gtv(mapOf())).asArray()
                        .map { it.asString() })
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
