package net.postchain.images.directory1

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.time.Duration

class GethContainer(
        logger: Slf4jLogConsumer,
        private val gethHost: String = "evm-host",
) : GenericContainer<GethContainer>(
        DockerImageName.parse("ethereum/client-go:v1.13.5")
) {

    init {
        super.addExposedPorts(8545)
        super.withNetworkAliases(gethHost)
        super.withLogConsumer(logger)
        super.withClasspathResourceMapping(this::class.java.getResource("evm/geth")!!.path.substringAfter("test-classes/"), "/geth", BindMode.READ_ONLY)
        super.withCreateContainerCmdModifier { cmd -> cmd.withEntrypoint("./geth/start.sh") }
        waitStrategy = LogMessageWaitStrategy()
                .withRegEx(".*HTTP server started.*\\s")
                .withTimes(1).withStartupTimeout(Duration.ofMinutes(2))
    }

    fun getNetworkGethUrl(): String = "http://$gethHost:8545"

    fun getExternalGethUrl(): String = "http://$host:${getMappedPort(8545)}"
}
