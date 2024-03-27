package net.postchain.images.directory1

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.InternetProtocol
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.time.Duration


class GethContainer(
        dockerImageName: DockerImageName = DockerImageName.parse("hadv/eth-client-go:v1.12.3"),
        logger: Slf4jLogConsumer
) : EvmContainer(dockerImageName, logger)

class BscContainer(
        dockerImageName: DockerImageName = DockerImageName.parse("hadv/bsc-client-go:v1.2.10"),
        logger: Slf4jLogConsumer
) : EvmContainer(dockerImageName, logger)

abstract class EvmContainer(
        dockerImageName: DockerImageName,
        logger: Slf4jLogConsumer,
        val gethHost: String = "evm-node",
        val gethPort: Int = 8545
) : GenericContainer<EvmContainer>(dockerImageName), Startable {

    init {
        super.addFixedExposedPort(gethPort, gethPort, InternetProtocol.TCP)
        super.withNetworkAliases(gethHost)
        super.withLogConsumer(logger)
        super.withCommand("--nousb", "--http", "--http.addr=0.0.0.0", "--http.vhosts=*",
                "--dev", "--dev.period=1", "--rpc.allow-unprotected-txs")
        waitStrategy = LogMessageWaitStrategy()
                .withRegEx(".*HTTP server started.*\\s")
                .withTimes(1).withStartupTimeout(Duration.ofMinutes(2))
    }

    fun getNetworkGethUrl(): String = "http://$gethHost:$gethPort"

    fun getExternalGethUrl(): String = "http://$host:${getMappedPort(gethPort)}"

}
