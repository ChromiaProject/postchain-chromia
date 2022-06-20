package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.*
import com.google.protobuf.ByteString
import com.spotify.docker.client.DockerClient
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
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
import net.postchain.images.common.ManagedModeBase
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

internal val initialProviderPubKey = adminPubKey.hexStringToByteArray()

@Disabled
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Directory1DeploymentIT {

    companion object : ManagedModeBase("directory1-deployment", "/directory1/rell", "/chain_zero/run-directory1.xml") {
        private val dockerClient: DockerClient = DockerClientFactory.create()
        private lateinit var dapp1: Pair<Long, BlockchainRid>
        private val resolvedDockerHost = getResolvedDockerHost()

        init {
            node3.withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                .withMasterDockerConfig()

        }


        @JvmStatic
        @BeforeAll
        fun setup() {
            startNodesAndChain0()
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopNodes()
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
        node1Db.awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider1`() {
        node1.txAsAdmin(brid, "init")
        node1.client(brid).querySync("get_all_providers").also {
            assert(it.asArray().size).isEqualTo(1)
        }
    }

    @Test
    @Order(3)
    fun `Add node1 to its own network`() {
        consoleLogger.info("Adding node1 to its own network")

        val provider = node1.client(brid).getProvider1()
        val cluster = node1.client(brid).getSystemCluster()

        node1.txAsAdmin(
            brid, "add_node",
            provider,
            gtv(node1.pubKeyByteArray),
            gtv(node1.nodeHost), gtv(node1.nodePort.toLong()),
            cluster
        )
        node1Db.awaitNewBlock()
        node1.client(brid).query(
            "is_node", gtv("pubkey" to gtv(node1.pubKeyByteArray))
        ).also {
            assert(it.get().asBoolean()).isTrue()
        }

        node1.client(brid).query(
            "get_node_data", gtv("pubkey" to gtv(node1.pubKeyByteArray))
        ).also {
            assert(it.get().asDict()["active"]!!.asInteger()).isEqualTo(1L)
        }
    }

    @Test
    @Order(4)
    fun `Make chain0 aware of itself`() {
        val provider = node1.client(brid).getProvider1()
        val container = node1.client(brid).getSystemContainer()
        node1.txAsAdmin(
            brid, "propose_blockchain", provider, gtv(chain0Config.readBytes()), container
        )
        node1Db.awaitNewBlock()
        assert(node1.getAllBlockchains(brid).asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Add node2 as signer to c0`() {
        consoleLogger.info("Adding node2 to the cluster")
        val provider1 = node1.client(brid).getProvider1()
        val cluster = node1.client(brid).getSystemCluster()

        consoleLogger.info("Registering provider2")
        val provider2 = Context(node1, node1Db, provider1)
            .registerNodeAsProvider(brid, cluster, node2)

        consoleLogger.info("Adding node2 to [node1] network")
        addNode(node2, provider2, cluster, brid, node1)

        // Asserting that node2 is signers of chain0
        val c0 = node1.getBlockchainGtv(brid, brid)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(brid, c0).size).isEqualTo(2)
            assert(node2.getBlockchainSigners(brid, c0).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        consoleLogger.info("Adding node3 to the cluster")
        val provider1 = node1.client(brid).getProvider1()
        val provider2 = node2.getProvider(brid)
        val cluster = node1.client(brid).getSystemCluster()

        consoleLogger.info("Registering provider3")
        val provider3 = Context(node1, node1Db, provider1, approverNode = node2, approver = provider2)
            .registerNodeAsProvider(brid, cluster, node3)

        consoleLogger.info("Adding node3 to [node1, node2] network")
        addNode(node3, provider3, cluster, brid, node1)

        // Asserting that node2 is signers of chain0
        val c0 = node1.getBlockchainGtv(brid, brid)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(brid, c0).size).isEqualTo(3)
            assert(node2.getBlockchainSigners(brid, c0).size).isEqualTo(3)
            assert(node3.getBlockchainSigners(brid, c0).size).isEqualTo(3)
        }
    }

    @Test
    @Order(7)
    fun `Deploy new dapp`() {
        consoleLogger.info("Deploy new dapp")
        listOf(node1, node2, node3).forEach { node ->
            Assumptions.assumeTrue {
                node.getAllBlockchains(brid).asArray().size == 1
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

        val provider1 = node1.client(brid).getProvider1()
        val provider2 = node2.getProvider(brid)
        val provider3 = node3.getProvider(brid)
        val container = node1.client(brid).getSystemContainer()
        rellConfig.config.chains.forEach { chain ->
            consoleLogger.info { "Adding test dapp ${chain.iid}" }
            chain.configs.forEach { (height, config) ->
                consoleLogger.info { "Proposing a blockchain on height $height" }
                dapp1 = 100L to GtvToBlockchainRidFactory.calculateBlockchainRid(config.gtvConfig)
                val configGtv = gtv(GtvEncoder.encodeGtv(config.gtvConfig))
                node3.tx(brid, "propose_blockchain", provider3, configGtv, container)

                // Voting
                val p1 = node1.approveProposal(brid, provider1)
                consoleLogger.info { "node1 voted for proposal: $p1" }

                val p2 = node2.approveProposal(brid, provider2)
                consoleLogger.info { "node2 voted for proposal: $p2" }
            }
        }

        // Asserting that blockchain is added
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                assert(node.getAllBlockchains(brid).asArray().size).isEqualTo(2)
            }
        }

        // Asserting that node1/node2/node3 are signers of newly added blockchain
        val c100 = node1.getBlockchainGtv(brid, dapp1.second)
        awaitUntilAsserted {
            assert(node1.getBlockchainSigners(brid, c100).size).isEqualTo(3)
            assert(node2.getBlockchainSigners(brid, c100).size).isEqualTo(3)
            assert(node3.getBlockchainSigners(brid, c100).size).isEqualTo(3)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        consoleLogger.info("Launch Subnode container")
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            assert(all.filter { it.image().contains("postchain-subnode") && it.state() == "running" }.size).isEqualTo(1)
        }
    }

    @Test
    @Order(9)
    fun `Transactions can be sent to newly deployed dapp`() {
        consoleLogger.info("Send TX to new dapp and fetch data")
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
