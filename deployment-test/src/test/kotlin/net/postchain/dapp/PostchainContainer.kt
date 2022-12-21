package net.postchain.dapp

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult

import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.InternetProtocol
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.net.URL
import java.time.Duration

const val adminPubKey = "030AB2EA43A545578C8BA13E0A6948E3A120E9C1AF87B9F864CCB469529CF4F653"
const val adminPrivKey = "69BC38753A11753354791A4F189F704C3D14B73B77AE3F55A4D466A991339541"

class PostchainContainer(
    dockerImageName: DockerImageName = DockerImageName.parse(System.getProperty("POSTCHAIN_TEST_DAPP_IMAGE", "chromaway/postchain-test-dapp:latest")),
    val appConfig: AppConfig,
    startupMsg: String = "Blockchain has been started",
    val nodePort: Int = appConfig.getInt("messaging.port"),
    val nodeHost: String = "",
    val provider: KeyPair = KeyPair.of(adminPubKey, adminPrivKey)
) : GenericContainer<PostchainContainer>(dockerImageName), Startable {

    val nodeKeyPair = KeyPair.of(appConfig.pubKey, appConfig.privKey)
    val pubkey get() = nodeKeyPair.pubKey
    val privkey get() = nodeKeyPair.privKey

    private val apiPort: Int = appConfig.getInt("api.port")

    private val bridMap = mutableMapOf<Long, String>()

    companion object {
        const val RELL_PATH = "/opt/chromaway/rell"
        const val POSTCHAIN_PATH = "/opt/chromaway/postchain"
        const val RELL_SRC = "$RELL_PATH/src"

        // Path that is OK to use on your local machine, so host machine can mount subnode config
        val MOUNT_DIR = System.getenv("TEST_MOUNT_DIRECTORY")
                ?: "/tmp/chromaway/postchain".also { File(it).deleteRecursively() }

        // Install location of docker socket
        private val DOCKER_SOCKET = System.getenv("DOCKER_SOCKET") ?: "/var/run/docker.sock"
    }

    init {
        withExposedPorts(apiPort)
        withFixedExposedPort(nodePort, nodePort)
        waitStrategy = LogMessageWaitStrategy()
                .withRegEx(".*$startupMsg.*\\s")
                .withTimes(1).withStartupTimeout(Duration.ofMinutes(2))
    }

    fun withFixedExposedPort(hostPort: Int, containerPort: Int): PostchainContainer {
        super.addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP)
        return self()
    }

    fun withMasterDockerConfig(): PostchainContainer {
        if (System.getenv("DOCKER_HOST") == null) {
            // Mount host machines docker socket into master container
            super.addFileSystemBind(DOCKER_SOCKET, DOCKER_SOCKET, BindMode.READ_ONLY)
        }
        // Mounting a volume that can be used as a "bridge" between the containers
        super.addFileSystemBind(MOUNT_DIR, MOUNT_DIR, BindMode.READ_WRITE)
        return self()
    }

    fun txAsAdmin(chainId: Long, opName: String, vararg args: Gtv) =
            txAsAdmin(BlockchainRid.buildFromHex(getBlockchainRidStr(chainId)), opName, *args)

    fun txAsAdmin(brid: BlockchainRid, opName: String, vararg args: Gtv): TransactionResult {
        return client(brid, listOf(KeyPair.of(adminPubKey, adminPrivKey))).transactionBuilder()
            .addOperation(opName, *args)
            .postTransactionUntilConfirmed(opName)
    }

    fun tx(chainId: Long, opName: String, vararg args: Gtv): TransactionResult =
            tx(BlockchainRid.buildFromHex(getBlockchainRidStr(chainId)), opName, *args)

    fun tx(brid: BlockchainRid, opName: String, vararg args: Gtv): TransactionResult {
        return client(brid).transactionBuilder()
            .addOperation(opName, *args)
            .postTransactionUntilConfirmed(opName)
    }

    fun getBlockchainRidStr(chainId: Long): String {
        return bridMap.getOrPut(chainId) {
            execInContainer("cat", "${envMap["RELL_OUT"] ?: "$RELL_PATH/out"}/blockchains/$chainId/brid.txt").stdout
        }
    }

    fun getBlockchainRid(chainId: Long): BlockchainRid {
        return BlockchainRid.buildFromHex(getBlockchainRidStr(chainId))
    }

    fun apiPath() = "http://$host:${getMappedPort(apiPort)}"

    fun client(chainId: Long, signers: List<KeyPair> = listOf(provider)) = client(getBlockchainRid(chainId), signers)
    fun client(brid: BlockchainRid, signers: List<KeyPair> = listOf(provider)) = createClient(brid, signers)

    private fun createClient(brid: BlockchainRid, signers: List<KeyPair>): PostchainClient {
        return AwaitingClient(PostchainClientImpl(PostchainClientConfig(brid, EndpointPool.singleUrl(apiPath()), signers)))
    }
}

fun parseConfig(url: URL, configOverrides: Map<String, Any?>? = null): AppConfig {
    return Parameters().properties()
            .setURL(url)
            .let {
                val config = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                        .configure(it)
                        .configuration

                if (configOverrides != null) {
                    configOverrides.forEach { (key, value) ->
                        config.setProperty(key, value)
                    }
                    // Overwrite file as well
                    ConfigurationUtils.dump(config, PrintWriter(FileWriter(url.file)))
                }

                AppConfig(config)
            }
}
