package net.postchain.dapp

import assertk.assert
import assertk.assertions.contains
import net.postchain.gtv.GtvDictionary
import net.postchain.postgres.ChromaWayPostgresContainer
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class PostchainContainerIT {

    private val network: Network = Network.newNetwork()

    @Container
    private val postgres = ChromaWayPostgresContainer()
            .withNetwork(network)
            .withEnv("POSTGRES_DB", "postchain")
            .withEnv("POSTGRES_PASSWORD", "postchain")
            .withEnv("POSTGRES_USER", "postchain")
            .withEnv("POSTGRES_INITDB_ARGS", "--lc-collate=C.UTF-8 --lc-ctype=C.UTF-8 --encoding=UTF-8")

    @Container
    private val postchain = PostchainContainer(appConfig = parseConfig(this::class.java.getResource("/simple-dapp/node-config.properties")!!))
            .withNetwork(network)
            .withClasspathResourceMapping("simple-dapp", "/opt/chromaway/postchain", BindMode.READ_ONLY)
            .withEnv("POSTCHAIN_DB_URL", postgres.networkJdbcUrl())
            .withCommand("run-node-auto")

    @Test
    fun `A simple dapp can start and handle queries`() {
        assert(postchain.client(1).query("hello_world", GtvDictionary.build(mapOf())).asString())
                .contains("Hello World!")
    }
}