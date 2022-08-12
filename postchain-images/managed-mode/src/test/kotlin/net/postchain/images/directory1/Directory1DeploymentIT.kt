package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.spotify.docker.client.DockerClient
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.PostchainContainer.Companion.MOUNT_DIR
import net.postchain.dapp.adminPubKey
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.*
import org.testcontainers.containers.BindMode
import org.testcontainers.junit.jupiter.Testcontainers

internal val initialProviderPubKey = adminPubKey.hexStringToByteArray()

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Directory1DeploymentIT {

    companion object : ManagedModeBase("/directory1/rell") {
        private val dockerClient: DockerClient = DockerClientFactory.create()
        private val dapps = mutableMapOf<Long, BlockchainRid>()
        private val resolvedDockerHost = getResolvedDockerHost()

        init {
            node3.withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                    .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                    .withMasterDockerConfig()
                    .withClasspathResourceMapping("${this::class.java.getResource("config")!!.path.substringAfter("test-classes/")}/node3",
                            MOUNT_DIR, BindMode.READ_ONLY)
                    .withEnv("POSTCHAIN_CONFIG", "$MOUNT_DIR/node-config.properties")
        }


        @JvmStatic
        @BeforeAll
        fun setup() {
            removeSubnodeContainers()
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

        val provider = node1.chain0.getProvider()
        val cluster = node1.chain0.getSystemCluster()

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
        val provider = node1.chain0.getProvider()
        val container = node1.chain0.getSystemContainer()
        node1.txAsAdmin(
                brid, "propose_blockchain", provider, gtv(chain0Config.readBytes()), container
        )
        node1Db.awaitNewBlock()
        assert(node1.chain0.getAllBlockchains().asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Add node2 as signer to c0`() {
        consoleLogger.info("Adding node2 to the cluster")
        val provider1 = node1.chain0.getProvider()
        val cluster = node1.chain0.getSystemCluster()

        consoleLogger.info("Registering provider2")
        val provider2 = Context(node1, node1Db, provider1)
                .registerNodeAsProvider(brid, cluster, node2)

        consoleLogger.info("Adding node2 to [node1] network")
        node1.chain0.addNode(node2, provider2, cluster, brid)

        // Asserting that node2 is signers of chain0
        val c0 = node1.chain0.getBlockchainGtv(brid)
        awaitUntilAsserted {
            assert(node1.chain0.getBlockchainSigners(c0).size).isEqualTo(2)
            assert(node2.chain0.getBlockchainSigners(c0).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        consoleLogger.info("Adding node3 to the cluster")
        val provider1 = node1.chain0.getProvider()
        val provider2 = node2.chain0.getProvider(node2.pubKeyByteArray)
        val cluster = node1.chain0.getSystemCluster()

        consoleLogger.info("Registering provider3")
        val provider3 = Context(node1, node1Db, provider1, approverNode = node2, approver = provider2)
                .registerNodeAsProvider(brid, cluster, node3)

        consoleLogger.info("Adding node3 to [node1, node2] network")
        node1.chain0.addNode(node3, provider3, cluster, brid)

        // Asserting that node2 is signers of chain0
        val c0 = node1.chain0.getBlockchainGtv(brid)
        awaitUntilAsserted {
            assert(node1.chain0.getBlockchainSigners(c0).size).isEqualTo(3)
            assert(node2.chain0.getBlockchainSigners(c0).size).isEqualTo(3)
            assert(node3.chain0.getBlockchainSigners(c0).size).isEqualTo(3)
        }
    }

    @Test
    @Order(7)
    fun `Deploy new dapp`() {
        deployDapp("test-dapp", 1, 2)
        deployDapp("test-dapp2", 2, 3)
    }

    private fun deployDapp(dappName: String, blockchainsBefore: Int, blockchainsAfter: Int) {
        consoleLogger.info("Deploy new dapp $dappName")
        listOf(node1, node2, node3).forEach { node ->
            Assumptions.assumeTrue {
                node.chain0.getAllBlockchains().asArray().size == blockchainsBefore
            }
        }
        val rellConfig = compileDapp(dappName)

        val provider1 = node1.chain0.getProvider()
        val provider2 = node2.chain0.getProvider(node2.pubKeyByteArray)
        val provider3 = node3.chain0.getProvider(node3.pubKeyByteArray)
        val container = node1.chain0.getSystemContainer()
        var blockchainRid: BlockchainRid? = null
        rellConfig.config.chains.forEach { chain ->
            consoleLogger.info { "Adding test dapp $dappName:${chain.iid}" }
            chain.configs.forEach { (height, config) ->
                blockchainRid = BlockchainRid(chain.brid.toByteArray())
                dapps[chain.iid] = blockchainRid!!
                consoleLogger.info { "Proposing a blockchain ${blockchainRid?.toShortHex()} with config at height $height" }

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
                assert(node.chain0.getAllBlockchains().asArray().size).isEqualTo(blockchainsAfter)
            }
        }

        // Asserting that node1/node2/node3 are signers of newly added blockchain
        val bcGtv = node1.chain0.getBlockchainGtv(blockchainRid!!)
        awaitUntilAsserted {
            assert(node1.chain0.getBlockchainSigners(bcGtv).size).isEqualTo(3)
            assert(node2.chain0.getBlockchainSigners(bcGtv).size).isEqualTo(3)
            assert(node3.chain0.getBlockchainSigners(bcGtv).size).isEqualTo(3)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        consoleLogger.info("Launch Subnode container(s)")
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            val runningSubnodes = all.filter { it.image().contains("postchain-subnode") && it.state() == "running" }
            assert(runningSubnodes.size).isEqualTo(2)
        }
    }

    @Test
    @Order(9)
    fun `Transactions can be sent to dapp 100`() {
        assertThatDappProcessesTx(dapps[100]!!, "add_city", "Heraklion", "get_cities")
    }

    @Test
    @Order(10)
    fun `Transactions can be sent to dapp 101`() {
        assertThatDappProcessesTx(dapps[101]!!, "add_book", "Mastering Bitcoin", "get_books")
    }

    private fun assertThatDappProcessesTx(brid: BlockchainRid, txOp: String, txArg: String, query: String) {
        consoleLogger.info("Send TX to new dapp ${brid.toShortHex()} and fetch data")
        node2.tx(brid, txOp, gtv(txArg))
        awaitUntilAsserted {
            listOf(node1, node2, node3).forEach { node ->
                val cities = awaitQueryResult { node.client(brid).querySync(query) }!!
                        .asArray().map { it.asString() }
                assert(cities).containsExactly(txArg)
            }
        }
    }

    val PostchainContainer.chain0 get() = Directory1Helper(this, brid)
}
