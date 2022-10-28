package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.spotify.docker.client.DockerClient
import mu.KotlinLogging
import net.postchain.chain0.common.addNodeOperation
import net.postchain.chain0.common.proposal.*
import net.postchain.chain0.common.queries.*
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.common.voting.makeVoteOperation
import net.postchain.chain0.container.container_op.createContainerOperation
import net.postchain.chain0.directory1.initOperation
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.docker.DockerClientFactory
import net.postchain.crypto.KeyPair
import net.postchain.containers.bpm.resources.*
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.PostchainContainer.Companion.MOUNT_DIR
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.*
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Directory1DeploymentIT {

    companion object : ManagedModeBase("/directory1/rell") {
        val subnodeLogger = KotlinLogging.logger("SubNode")
        private val dockerClient: DockerClient = DockerClientFactory.create()
        private val dapps = mutableMapOf<Long, BlockchainRid>()
        private val resolvedDockerHost = getResolvedDockerHost()
        private const val systemContainer = "system"
        private const val foobarContainer = "foobar"
        private val resourceLimitsValues = Triple(600L, 250L, -1L) // (ram, cpu, storage)
        private val foobarResourceLimits = ContainerResourceLimits(
                Cpu(resourceLimitsValues.first), Ram(resourceLimitsValues.second), Storage(resourceLimitsValues.third)
        )

        init {
            node3.withEnv("DOCKER_HOST", resolvedDockerHost?.toString())
                    .withFixedExposedPort(9874, 9874) // Exposing port for subnode to connect to containerChains.masterPort
                    .withMasterDockerConfig()
                    .withClasspathResourceMapping(
                            "${this::class.java.getResource("config")!!.path.substringAfter("test-classes/")}/node3",
                            MOUNT_DIR, BindMode.READ_ONLY
                    )
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
            printSubnodeLogs(dockerClient, subnodeLogger)
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
        with(node1.c0) {
            transactionBuilder()
                    .initOperation(node1.nodeHost, node1.nodePort.toLong())
                    .postTransactionUntilConfirmed("init")

            assert(getSummary().providers).isEqualTo(1L)
            assert(isNode(node1.nodeKeyPair.pubKey)).isTrue()
            assert(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
        }
    }

    @Test
    @Order(3)
    fun `Add new container`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assert(getSummary().containers).isEqualTo(1L)
            }

            transactionBuilder()
                    .createContainerOperation(
                            node1.providerPubkey,
                            foobarContainer,
                            "system",
                            1,
                            listOf(node1.provider.pubKey.data)
                    )
                    .postTransactionUntilConfirmed("$foobarContainer container")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertEquals(setOf(systemContainer, foobarContainer), containers)
            }
        }
    }

    @Test
    @Order(4)
    fun `Add container resource limits`() {
        // Asserting that resource limits are defaults
        val expectedLimits = ContainerResourceLimits(Cpu(-1L), Ram(-1L), Storage(-1L))
        val actualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertEquals(expectedLimits, actualLimits)

        // Changing resource limits
        with(resourceLimitsValues) {
            node1.c0.transactionBuilder().proposeContainerLimitsOperation(
                    node1.providerPubkey,
                    foobarContainer,
                    mapOf(cpu to first, ram to second, storage to third)
            ).postTransactionUntilConfirmed("container limits")
        }

        // Asserting resource limits changed
        val newActualLimits = ContainerResourceLimits(*queryContainerResourceLimits())
        assertEquals(foobarResourceLimits, newActualLimits)
    }

    @Test
    @Order(5)
    fun `Add node2 as signer to c0`() {
        consoleLogger.info("Adding node2 to the cluster")
        consoleLogger.info("Registering provider2")
        node1.client(brid, listOf(node1.provider, node2.provider)).transactionBuilder()
            .registerProviderOperation(node1.providerPubkey, node2.provider.pubKey, ProviderTier.NODE_PROVIDER)
            .proposeProviderIsSystemOperation(node1.providerPubkey, node2.providerPubkey, true)
            .postTransactionUntilConfirmed("Register p2 as system")

        voteOnAllProposals(node2.provider)

        node1.client(brid, listOf(node2.provider)).transactionBuilder()
            .addNodeOperation(
                node2.providerPubkey,
                node2.nodeKeyPair.pubKey.data,
                node2.nodeHost,
                node2.nodePort.toLong(),
                node2.apiPath(),
                listOf("system")
            )
            .postTransactionUntilConfirmed("add node 2 to system cluster")
        // Asserting that node2 is signers of chain0
        awaitQueryResult {
            assert(node1.c0.getBcSigners(brid).size).isEqualTo(2)
            assert(node2.c0.getBcSigners(brid).size).isEqualTo(2)
        }
    }

    @Test
    @Order(6)
    fun `Add node3 as signer to c0`() {
        consoleLogger.info("Adding node3 to the cluster")
        consoleLogger.info("Registering provider3")
        node1Db.awaitNewBlock()
        node1.client(brid, listOf(node1.provider, node2.provider)).transactionBuilder()
            .registerProviderOperation(node1.providerPubkey, node3.provider.pubKey, ProviderTier.NODE_PROVIDER)
            .proposeProviderIsSystemOperation(node1.providerPubkey, node3.providerPubkey, true)
            .postTransactionUntilConfirmed("Register p3 as system")

        voteOnAllProposals(node2.provider)
        voteOnAllProposals(node3.provider)

        consoleLogger.info("Adding node3 to [node1, node2] network")
        node1.client(brid, listOf(node3.provider)).transactionBuilder()
            .addNodeOperation(
                node3.providerPubkey,
                node3.pubkey.data,
                node3.nodeHost,
                node3.nodePort.toLong(),
                node3.apiPath(),
                listOf("system")
            )
            .postTransactionUntilConfirmed("add node 3 to system cluster")

        // Asserting that node2 is signers of chain0
        awaitQueryResult {
            assert(node1.c0.getBcSigners(brid).size).isEqualTo(3)
            assert(node2.c0.getBcSigners(brid).size).isEqualTo(3)
            assert(node3.c0.getBcSigners(brid).size).isEqualTo(3)
        }
    }

    private fun voteOnAllProposals(provider: KeyPair) {
        node1.c0.getProposalsSince(RowId(0)).sortedBy { it.rowid.id }.forEach {
            node1.client(brid, listOf(provider)).transactionBuilder()
                .makeVoteOperation(provider.pubKey.data, it.rowid.id, true)
                .postTransactionUntilConfirmed("provider ${provider.pubKey.hex()} vote on ${it.rowid}, ${it.proposalType}")
        }
    }

    @Test
    @Order(7)
    fun `Deploy new dapp`() {
        listOf(node1, node2, node3).forEach { node ->
            assert(node.c0.getBlockchains(true).size).isEqualTo(1)
        }

        deployDapp("test-dapp", systemContainer)
        deployDapp("test-dapp2", foobarContainer)

        // Asserting that blockchain is added
        listOf(node1, node2, node3).forEach { node ->
            assert(node.c0.getBlockchains(true).size).isEqualTo(3)
        }
    }

    private fun deployDapp(dappName: String, containerName: String) {
        consoleLogger.info("Deploy new dapp $dappName")

        val rellConfig = compileDapp(dappName)

        var blockchainRid: BlockchainRid? = null
        rellConfig.config.chains.forEach { chain ->
            consoleLogger.info { "Adding test dapp $dappName:${chain.iid}" }
            chain.configs.forEach { (height, config) ->
                blockchainRid = BlockchainRid(chain.brid.toByteArray())
                dapps[chain.iid] = blockchainRid!!
                consoleLogger.info { "Proposing a blockchain ${blockchainRid?.toShortHex()} with config at height $height" }

                node3Db.awaitNewBlock()
                val configGtv = GtvEncoder.encodeGtv(config.gtvConfig)
                node1.c0.transactionBuilder()
                        .proposeBlockchainOperation(node1.providerPubkey, configGtv, "dapp", containerName)
                        .postTransactionUntilConfirmed("Propose dapp $blockchainRid")

                voteOnAllProposals(node2.provider)
                voteOnAllProposals(node3.provider)
            }
        }

        // Asserting that node1/node2/node3 are signers of newly added blockchain
        awaitQueryResult {
            assert(node1.c0.getBcSigners(blockchainRid!!).size).isEqualTo(3)
            assert(node2.c0.getBcSigners(blockchainRid!!).size).isEqualTo(3)
            assert(node3.c0.getBcSigners(blockchainRid!!).size).isEqualTo(3)
        }
    }

    @Test
    @Order(8)
    fun `Subnode container has been launched`() {
        consoleLogger.info("Asserting that subnode container(s) launched")
        awaitUntilAsserted {
            val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
            val runningSubnodes = all.filter { it.image().contains("postchain-subnode") && it.state() == "running" }
            assert(runningSubnodes.size).isEqualTo(2)
        }
    }

    @Test
    @Order(9)
    fun `Subnode container has resource limits`() {
        consoleLogger.info("Asserting container resource limits")

        val expectedResourceLimits = ContainerResourceLimits(
                Cpu(resourceLimitsValues.first), Ram(resourceLimitsValues.second), Storage(resourceLimitsValues.third)
        )

        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        all.forEach {
            if (it.names()?.get(0)?.contains(foobarContainer) == true) {
                val res = dockerClient.inspectContainer(it.id())
                Assertions.assertEquals(expectedResourceLimits.ramBytes(), res.hostConfig()?.memory())
                Assertions.assertEquals(expectedResourceLimits.cpuQuota(), res.hostConfig()?.cpuQuota())
            }
        }
    }

    @Test
    @Order(10)
    fun `Transactions can be sent to dapp 100`() {
        assertThatDappProcessesTx(dapps[100]!!, "add_city", "Heraklion", "get_cities")
    }

    @Test
    @Order(11)
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

    private fun queryContainerResourceLimits(): Array<ResourceLimit> {
        return node1.c0.nmGetContainerLimits(foobarContainer)
                .mapNotNull {
                    ResourceLimitFactory.fromPair(it.toPair())
                }.toTypedArray()
    }

    private val PostchainContainer.c0 get() = client(brid)

    val PostchainContainer.providerPubkey get() = provider.pubKey.data
}
