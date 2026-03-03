package net.postchain.dapp

import com.github.dockerjava.api.model.ExposedPort
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import net.postchain.client.config.PostchainClientConfig
import net.postchain.images.directory1.AwaitingClient
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.crypto.KeyPair
import net.postchain.d1.cluster.D1PeerInfo
import net.postchain.gtv.Gtv
import net.postchain.gtx.Gtx
import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.io.FileWriter
import java.io.PrintWriter
import java.net.URL
import java.time.Duration

const val adminPubKey = "030AB2EA43A545578C8BA13E0A6948E3A120E9C1AF87B9F864CCB469529CF4F653"
const val adminPrivKey = "69BC38753A11753354791A4F189F704C3D14B73B77AE3F55A4D466A991339541"

class PostchainContainer(
        dockerImageName: DockerImageName = DockerImageName.parse(System.getProperty("POSTCHAIN_IMAGE", "chromaway/chromia-server:latest")),
        val appConfig: AppConfig,
        startupMsg: String = "Postchain node is running",
        val nodePort: Int = appConfig.getInt("messaging.port"),
        val nodeHost: String = "",
        val provider: KeyPair = KeyPair.of(adminPubKey, adminPrivKey)
) : GenericContainer<PostchainContainer>(dockerImageName), Startable {

    val nodeKeyPair = KeyPair.of(appConfig.pubKey, appConfig.privKey)
    val pubkey get() = nodeKeyPair.pubKey
    val privkey get() = nodeKeyPair.privKey
    private val apiPort: Int = appConfig.getInt("api.port")
    private val bridMap = mutableMapOf<Long, String>()
    lateinit var channel: ManagedChannel

    companion object {
        const val POSTCHAIN_PATH = "/opt/chromaway/postchain"
    }

    init {
        withExposedPorts(apiPort, nodePort)
        waitStrategy = LogMessageWaitStrategy()
                .withRegEx(".*$startupMsg.*\\s")
                .withTimes(1).withStartupTimeout(Duration.ofMinutes(2))
    }

    fun txAsAdmin(chainId: Long, opName: String, vararg args: Gtv) =
            txAsAdmin(BlockchainRid.buildFromHex(getBlockchainRidStr(chainId)), opName, *args)

    fun txAsAdmin(brid: BlockchainRid, opName: String, vararg args: Gtv): TransactionResult {
        return client(brid, listOf(KeyPair.of(adminPubKey, adminPrivKey))).transactionBuilder()
                .addOperation(opName, *args)
                .postTransactionUntilConfirmed(opName)
    }

    fun tx(brid: BlockchainRid, opName: String, vararg args: Gtv): Pair<Gtx, TransactionResult> {
        val txBuilder = client(brid).transactionBuilder(listOf())
                .addOperation(opName, *args)
        val txResult = txBuilder
                .postTransactionUntilConfirmed(opName)
        val tx = txBuilder.finish().buildGtx()
        return tx to txResult
    }

    private fun getBlockchainRid(chainId: Long) = BlockchainRid.buildFromHex(getBlockchainRidStr(chainId))

    private fun getBlockchainRidStr(chainId: Long): String {
        return bridMap.getOrPut(chainId) {
            execInContainer("cat", "${envMap["RELL_OUT"] ?: POSTCHAIN_PATH}/blockchains/$chainId/brid.txt").stdout
        }
    }

    fun nodeApiPath() = "http://$nodeHost:$apiPort"
    fun apiPath() = "http://${this.host}:${getMappedPort(apiPort)}"

    fun client(chainId: Long, signers: List<KeyPair> = listOf(provider), merkleHashVersion: Int = 2) = client(getBlockchainRid(chainId), signers, merkleHashVersion)
    fun client(brid: BlockchainRid, signers: List<KeyPair> = listOf(provider), merkleHashVersion: Int = 2) =
            createClient(brid, signers, merkleHashVersion)

    private fun createClient(brid: BlockchainRid, signers: List<KeyPair>, merkleHashVersion: Int): PostchainClient =
            AwaitingClient(PostchainClientImpl(PostchainClientConfig(brid, EndpointPool.singleUrl(apiPath()), signers, merkleHashVersion = merkleHashVersion)))

    fun peerInfo(): D1PeerInfo = D1PeerInfo(apiPath(), pubkey)

    override fun start() {

        super.start()

        if (this.containerInfo.networkSettings.ports.bindings.containsKey(ExposedPort(50051))) {
            channel = ManagedChannelBuilder.forTarget("${this.host}:${this.getMappedPort(50051)}")
                    .usePlaintext().build()
        }
    }

    fun withGenesisNode(node: PostchainContainer): PostchainContainer {
        withEnv("POSTCHAIN_GENESIS_PUBKEY", node.pubkey.hex())
        withEnv("POSTCHAIN_GENESIS_HOST", node.nodeHost)
        withEnv("POSTCHAIN_GENESIS_PORT", node.nodePort.toString())
        return this
    }

    override fun stop() {
        if (::channel.isInitialized) {
            channel.shutdownNow()
        }
        super.stop()
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