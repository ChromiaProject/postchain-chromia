package net.postchain.images.common

import assertk.assertThat
import assertk.assertions.contains
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import mu.KotlinLogging
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetPeerInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetBlockchainConfigurationV5
import net.postchain.chain0.proposal.getRelevantProposals
import net.postchain.chain0.proposal.voting.makeVoteOperation
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.dapp.startContainers
import net.postchain.dapp.stopContainers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.Gtx
import net.postchain.images.directory1.awaitQueryResult
import net.postchain.images.directory1.getResolvedDockerHost
import net.postchain.images.directory1.saveSubnodeLogs
import net.postchain.images.directory1.setupMasterNodeConfig
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.postgres.ChromaWayPostgresContainer
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import org.junit.jupiter.api.Assertions.assertEquals
import org.mandas.docker.client.DockerClient
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.io.File

// Base class for managed mode tests
open class ManagedModeBase {

    protected val cryptoSystem = Secp256K1CryptoSystem()

    val testLogger = KotlinLogging.logger("TestLogger")

    val network: Network = Network.newNetwork()

    val postgres: ChromaWayPostgresContainer = ChromaWayPostgresContainer(DockerImages.postgresImage())
            .withNetwork(network)

    lateinit var node1: PostchainContainer
    lateinit var node2: PostchainContainer
    lateinit var node3: PostchainContainer

    val resolvedDockerHost = getResolvedDockerHost()
    protected val dockerClient: DockerClient = DockerClientFactory.create()
    protected val dapps = mutableMapOf<String, BlockchainRid>()
    lateinit var clusterAnchoringBrid: BlockchainRid
    lateinit var systemAnchoringBrid: BlockchainRid
    protected val dappTxs = mutableMapOf<BlockchainRid, Gtx>()
    protected val systemCluster = "system"
    protected val systemContainer = "system"

    fun nodes() = arrayOf(node1, node2, node3)

    fun breakdown() {
        saveSubnodeLogs(dockerClient)
        stopNodes()
        removeSubnodeContainers()

        /*
            This is used by the CI to run a shell command right before the
            files in the directory referenced by MOUNT_DIR are removed. It
            is necessary because the permissions need to be altered, since
            the files are owned by the root user account.
        */
        val testBreakdownCommand = System.getenv("TEST_BREAKDOWN_COMMAND")

        if (testBreakdownCommand != null) {
            ProcessBuilder(testBreakdownCommand)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
        }

        if (!File(PostchainContainer.MOUNT_DIR).deleteRecursively()) {
            testLogger.error("Unable to clear mount directory")
        }
    }

    fun removeSubnodeContainers() {
        dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()).forEach {
            if (it.image().contains("chromia-subnode")) {
                dockerClient.stopContainer(it.id(), 0)
                dockerClient.removeContainer(it.id())
            }
        }
    }

    fun postchainServer(hostName: String, logConsumer: Slf4jLogConsumer?, provider: KeyPair, configDir: String): PostchainContainer {
        val appConfig = setupMasterNodeConfig(this::class.java.getResource("$configDir/$hostName/node-config.properties")!!)
        return PostchainContainer(
                DockerImages.chromiaServerImage(),
                appConfig,
                startupMsg = "Postchain server started, listening on 50051",
                nodeHost = hostName,
                provider = provider
        )
                .withNetworkAliases(hostName)
                .withNetwork(this@ManagedModeBase.network)
                .withExposedPorts(50051, appConfig.getInt("api.port"))
                .withClasspathResourceMapping("${this::class.java.getResource(configDir)!!.path.substringAfter("test-classes/")}/${hostName}", "/config", BindMode.READ_ONLY)
                .withClasspathResourceMapping(this::class.java.getResource("/log")!!.path.substringAfter("test-classes/"), "/opt/chromaway/postchain", BindMode.READ_ONLY)
                .withEnv("POSTCHAIN_DEBUG", "true")
                .withEnv("POSTCHAIN_CONFIG", "/config/node-config.properties")
                .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
                .withLogConsumer(logConsumer)
                .withCommand("run-server")
    }

    var chain0Config: String = this::class.java.getResource("/directory1deployment/manager.xml")!!.readText()
    lateinit var chain0Brid: BlockchainRid

    lateinit var node1Db: ChainDatabaseCommunicator
    lateinit var node2Db: ChainDatabaseCommunicator
    lateinit var node3Db: ChainDatabaseCommunicator

    private lateinit var channel1: ManagedChannel
    private lateinit var channel2: ManagedChannel
    private lateinit var channel3: ManagedChannel

    fun stopNodes() {
        if (::channel1.isInitialized) channel1.shutdownNow()
        if (::channel2.isInitialized) channel2.shutdownNow()
        if (::channel3.isInitialized) channel3.shutdownNow()
        stopContainers(*nodes())
        postgres.stop()
    }

    fun startNodesAndChain0() {
        testLogger.info { "Starting nodes..." }
        postgres.start()
        startContainers(*nodes())

        // node1
        channel1 = createChannel(node1).usePlaintext().build()
        chain0Brid = startBlockchain(channel1, chain0Config)
                .let { BlockchainRid.buildFromHex(it) }
        testLogger.info("Chain0 bc-rid: ${chain0Brid.toHex()}")
        node1Db = postgres.createChainDatabaseCommunicator(0, node1.appConfig.databaseSchema)

        // node2
        if (::node2.isInitialized) {
            channel2 = createChannel(node2).usePlaintext().build()
            addPeer(channel2, node1)
            startBlockchain(channel2, chain0Config)
            node2Db = postgres.createChainDatabaseCommunicator(0, node2.appConfig.databaseSchema)
        }

        // node3
        if (::node3.isInitialized) {
            channel3 = createChannel(node3).usePlaintext().build()
            addPeer(channel3, node1)
            startBlockchain(channel3, chain0Config)
            node3Db = postgres.createChainDatabaseCommunicator(0, node3.appConfig.databaseSchema)
        }
    }

    private fun createChannel(target: PostchainContainer) =
            ManagedChannelBuilder.forTarget("${target.host}:${target.getMappedPort(50051)}")

    private fun addPeer(channel: ManagedChannel, peer: PostchainContainer) {
        val service = PeerServiceGrpc.newBlockingStub(channel)
        service.addPeer(
                AddPeerRequest.newBuilder()
                        .setHost(peer.nodeHost)
                        .setPort(peer.nodePort)
                        .setPubkey(peer.pubkey.hex())
                        .build()
        )
    }

    private fun startBlockchain(channel: ManagedChannel, config: String): String {
        return PostchainServiceGrpc.newBlockingStub(channel)
                .initializeBlockchain(
                        InitializeBlockchainRequest.newBuilder()
                                .setChainId(0)
                                .setGtv(ByteString.copyFrom(GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(config))))
                                .build()
                ).brid
    }

    protected fun assertAnchoringChainProperties() {
        val systemChains = node1.c0.nmComputeBlockchainInfoList(node1.nodeKeyPair.pubKey.data)
                .filter { it.system }.map { BlockchainRid(it.rid) }
        assertEquals(3, systemChains.size)

        // Getting cluster anchoring chain for system cluster via CM API
        clusterAnchoringBrid = BlockchainRid(node1.c0.cmGetClusterInfo(systemCluster).anchoringChain)
        // Asserting cluster anchoring chain is in system_chains list of NM API
        assertThat(systemChains.map { it }).contains(clusterAnchoringBrid)
        testLogger.info("Cluster anchor chain bc-rid: $clusterAnchoringBrid")

        systemAnchoringBrid = BlockchainRid(node1.c0.cmGetSystemAnchoringChain()!!)
        assertThat(systemChains.map { it }).contains(systemAnchoringBrid)
        testLogger.info("System anchor chain bc-rid: $systemAnchoringBrid")
    }

    protected fun voteOnAllProposals(providers: List<KeyPair>) {
        val txBuilder = node1.client(chain0Brid, providers).transactionBuilder()

        providers.forEach { provider ->
            val proposals = awaitQueryResult {
                node1.c0.getRelevantProposals(0, Long.MAX_VALUE, true, provider.pubKey.data)
            } ?: return

            proposals.forEach { proposal ->
                txBuilder.makeVoteOperation(provider.pubKey.data, proposal.rowid.id, true)
            }
        }

        txBuilder.postTransactionUntilConfirmed("providers vote on all proposals")
    }

    protected fun assertChainSigners(blockchainRid: BlockchainRid, vararg nodes: PostchainContainer) {
        awaitQueryResult {
            val currentHeight = node1.client(blockchainRid).currentBlockHeight()
            val actual = node1.c0.cmGetPeerInfo(blockchainRid.data, currentHeight).map { PubKey(it) }.toSet()
            val expected = nodes.map { it.pubkey }.toSet()
            assertEquals(expected, actual)
        }
    }

    protected fun getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node: PostchainContainer, blockchainRid: BlockchainRid): Set<Int> {
        val res = mutableSetOf<Int>()
        var current: Long? = 0L

        while (current != null) {
            val config = node.c0.nmGetBlockchainConfiguration(blockchainRid, current) ?: break
            res.add(GtvDecoder.decodeGtv(config).asDict()["blockstrategy"]!!["maxblocktransactions"]!!.asInteger().toInt())
            current = node.c0.nmFindNextConfigurationHeight(blockchainRid, current)
        }

        return res
    }

    protected fun getLastBlockConfigSigners(node: PostchainContainer, blockchainRid: BlockchainRid): List<WrappedByteArray> {
        val lastHeight = node1.client(blockchainRid).currentBlockHeight()
        return node.c0.nmGetBlockchainConfigurationV5(blockchainRid, lastHeight)!!.signers
    }

    protected fun compileDapp(dappName: String, maxBlockTransactions: Int = 500, iccfReceiver: ByteArray? = null, faulty: Boolean = false): Gtv =
            GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/$dappName.xml")!!.readText()
                    .replace("<int>500</int>", "<int>$maxBlockTransactions</int>")
                    .let {
                        if (iccfReceiver != null) it.replace("<string>DAPP_BRID</string>", "<bytea>${iccfReceiver.toHex()}</bytea>") else it
                    }
                    .let {
                        if (faulty) it.replace("<string>net.postchain.gtx.StandardOpsGTXModule</string>",
                                "<string>net.postchain.gtx.StandardOpsGTXModule</string>\n<string>unknown_module</string>") else it
                    })

    protected val PostchainContainer.c0 get() = client(chain0Brid)
    protected val PostchainContainer.providerPubkey get() = provider.pubKey.data
}
