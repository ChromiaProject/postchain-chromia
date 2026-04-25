package net.postchain.dapp

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import net.postchain.client.core.PostchainClient
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.postgres.ChromaWayPostgresContainer
import org.awaitility.Duration.FIVE_MINUTES
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
internal class MultiNodeLegacyIT {

    companion object {
        const val resourceFolder = "multi-node-config-dapp"
        private val network: Network = Network.newNetwork()

        private val postgres = ChromaWayPostgresContainer()
                .withNetwork(network)
                .apply {
                    startContainers(this)
                }
    }

    private val node1 = PostchainContainer(appConfig = parseConfig(this::class.java.getResource("/$resourceFolder/node1/node-config.properties")!!))
            .withNetwork(network)
            .withNetworkAliases("node1")
            .withClasspathResourceMapping("$resourceFolder/node1", PostchainContainer.POSTCHAIN_PATH, BindMode.READ_ONLY)
            .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
            .withEnv("POSTCHAIN_CONFIG", "${PostchainContainer.POSTCHAIN_PATH}/node-config.properties")
            .withCommand("run-node-auto")

    private val node2 = PostchainContainer(appConfig = parseConfig(this::class.java.getResource("/$resourceFolder/node2/node-config.properties")!!))
            .withNetwork(network)
            .withNetworkAliases("node2")
            .withClasspathResourceMapping("$resourceFolder/node2", PostchainContainer.POSTCHAIN_PATH, BindMode.READ_ONLY)
            .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
            .withEnv("POSTCHAIN_CONFIG", "${PostchainContainer.POSTCHAIN_PATH}/node-config.properties")
            .withCommand("run-node-auto")

    @BeforeEach
    fun setup() {
        startContainers(node1, node2)
    }

    @AfterEach
    fun breakdown() {
        stopContainers(node1, node2)
    }

    @Test
    fun `A transaction sent to node 1 can be retrieved from the whole network after a block was built`() {
        val chainId = 1L
        val dummyCity = "sthlm"
        val currentHeight = node1.client(chainId).getBlockchainHeight()

        // Given empty databases
        assertThat(node1.client(chainId).getCities()).isEmpty()
        assertThat(node2.client(chainId).getCities()).isEmpty()

        // When a transaction is added to one of the nodes
        node1.txAsAdmin(chainId, "add_city", gtv(dummyCity))

        // And a new block has been built
        node1.client(chainId).waitForHeight(currentHeight + 1, FIVE_MINUTES)
        node2.client(chainId).waitForHeight(currentHeight + 1, FIVE_MINUTES)

        // Then the transaction can be found in the entire network
        assertThat(node1.client(chainId).getCities()).contains(dummyCity)
        assertThat(node2.client(chainId).getCities()).contains(dummyCity)
    }
}

fun PostchainClient.getCities(): Collection<String> = this.query("get_cities", gtv(mapOf())).asArray().map { it.asString() }

fun PostchainClient.getBlockchainHeight(): Long =
    query("last_block_info", GtvDictionary.build(mapOf())).asDict()["height"]!!.asInteger()

fun PostchainClient.waitForHeight(height: Number, timeOut: org.awaitility.Duration) {
    await.pollInterval(org.awaitility.Duration.FIVE_SECONDS).atMost(timeOut).until {
        getBlockchainHeight() >= height.toLong()
    }
}
