package net.postchain.directory1.test

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.base.configuration.KEY_QUEUE_CAPACITY
import net.postchain.chain0.common.queries.GetNodesWithProviderResult
import net.postchain.chain0.common.queries.getBlockchainReplicas
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getProviderData
import net.postchain.chain0.common.queries.listClustersOfNode
import net.postchain.chain0.common.operations.registerNodeOperation
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.impl.PostchainClientProviderImpl
import net.postchain.client.request.EndpointPool
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.BlockchainSetupFactory
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val PostchainClient.pubkey get() = config.pubkey().data
fun PostchainClientConfig.pubkey() = signers.first().pubKey
fun PostchainClientConfig.privkey() = signers.first().privKey

abstract class ManagedModeTest : IntegrationTestSetup() {

    val node1Pubkey = KeyPairHelper.pubKeyHex(1)
    val node1Host = "127.0.0.1"
    val node1Port = 9871L

    val node0Host = "127.0.0.1"
    val node0Port = 9870L

    private val adminKey = 10
    private val providerKey = 11
    private val providerKey2 = 12
    private val clientConfig by lazy { cliConf(adminKey) }
    val provConfig by lazy { cliConf(providerKey) }
    val prov2Config by lazy { cliConf(providerKey2) }
    open val provClient by lazy { fromConfig(provConfig) }
    open val prov2Client by lazy { fromConfig(prov2Config) }

    // Voter sets SYSTEM and SYSTEM_P are created during initialization.
    val voterSetSystemP = "SYSTEM_P"
    val voterSetSystem = "SYSTEM"
    val systemClusterName = "system"
    val systemContainerName = "system"

    companion object {
        val bcConfig1xmlFile = getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_1.xml")
        val bcConfig1xmlDependencyFile =
                getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_1_dependency.xml")

        fun getFileFromClasspath(path: String): File {
            val tempFile = File.createTempFile("managed-mode-test", "")
            val inputStream = Companion::class.java.getResourceAsStream(path)!!
            tempFile.writeBytes(inputStream.readAllBytes())
            return tempFile
        }
    }

    private fun getPostchainClient(appConfig: PostchainClientConfig): PostchainClient {
        return PostchainClientProviderImpl().createClient(appConfig)
    }

    protected fun assertProviderData(provPubkey: PubKey, name: String, isActive: Boolean?) {
        val data = provClient.getProviderData(provPubkey)
        assertArrayEquals(data.pubkey.data, provPubkey.data)
        assert(data.name).isEqualTo(name)
        assert(data.active).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(providerPublicKey: PubKey) {
        val data = provClient.getProviderData(providerPublicKey)
        assert(data.active).isEqualTo(true)
    }

    protected fun assertProviderDisabled(providerPublicKey: PubKey) {
        val data = provClient.getProviderData(providerPublicKey)
        assert(data.active).isEqualTo(false)
        val listReplicas = provClient.getBlockchainReplicas(clientConfig.blockchainRid)
        assertEquals(0, listReplicas.size)

        val listSigners = provClient.getBlockchainSigners(clientConfig.blockchainRid)
        assertEquals(1, listSigners.size)
    }

    protected fun assertNextConfiguration(config: PostchainClientConfig, expectedHeight: Long, expectedQueueCapacity: Long? = null) {
        // Get next configuration height of new blockchain configuration
        val lastHeight = getPostchainClient(cliConf(adminKey, config.blockchainRid)).currentBlockHeight()
        assertTrue(lastHeight >= 0)
        val chain0Client = getPostchainClient(config)
        val actualHeight = chain0Client.nmFindNextConfigurationHeight(config.blockchainRid, lastHeight)
        assert(actualHeight).isEqualTo(expectedHeight)

        // Get next configuration
        val bc = chain0Client.nmGetBlockchainConfiguration(config.blockchainRid, actualHeight!!)
        assert(bc).isNotNull()

        if (expectedQueueCapacity != null) {
            val gtv = GtvDecoder.decodeGtv(bc!!)
            assertEquals(expectedQueueCapacity, gtv[KEY_QUEUE_CAPACITY]?.asInteger())
        }
    }

    /**
     * Add node; optionally also add it to a cluster
     */
    fun addNode(client: PostchainClient, key: String, host: String, port: Long, clusterName: String) {
        client.transactionBuilder().addNop().registerNodeOperation(
                client.config.pubkey().data,
                key.hexStringToByteArray(),
                host, port, "http://rest-api",
                if (clusterName == "") listOf() else listOf(clusterName)
        ).post()
        buildAndAwaitBlocks(1)
        assertAddedNode(client.config.pubkey().hex(), key, host, port, clusterName, "http://rest-api")
    }

    fun assertAdded(opName: String, keyName: String, addedItem: Gtv) {
        val client = getPostchainClient(provConfig)
        val vs = client.query(opName, GtvFactory.gtv(keyName to addedItem))
        assert(vs.asInteger()).isGreaterThan(0L)
    }

    private fun assertAddedNode(providerPublicKey: String, nodePubkey: String, host: String, port: Long, cluster: String, apiUrl: String) {
        awaitUntilAsserted {
            val nodePK = PubKey(nodePubkey)
            val nodeData = provClient.getNodeData(nodePK)
            assert(nodeData.active).isEqualTo(true)
            assert(nodeData.host).isEqualTo(host)
            assert(nodeData.port).isEqualTo(port)
            assertEquals(nodeData.provider, providerPublicKey.hexStringToWrappedByteArray())
            assertEquals(nodeData.pubkey, nodePK.wData)
            assertEquals(nodeData.apiUrl, apiUrl)
            if (cluster != "") {
                val clusters = provClient.listClustersOfNode(nodePK)
                assertEquals(clusters, listOf(cluster))
            }
        }
    }

    protected fun assertNodeInfo(
            n: GetNodesWithProviderResult,
            nodeHost: String,
            nodePort: Long,
            nodePubkey: PubKey,
            providerPubkey: PubKey,
            b: Boolean
    ) {
        assertEquals(nodeHost, n.host)
        assertEquals(nodePort, n.port)
        assertEquals(nodePubkey.wData, n.pubkey)

        assertEquals(providerPubkey.wData, n.provider)
        assertEquals(b, n.providerActive)
    }

    protected fun buildAndAwaitBlocks(nBlocks: Int, await: Boolean = true) {
        strategy().apply {
            val height = committedHeight + nBlocks
            buildBlocksUpTo(height.toLong())
            awaitCommitted(height)
        }

        if (await) {
            Thread.sleep(2000) // former awaitBlockchainReload()
        }
    }

    private fun strategy(): SmartOnDemandBlockBuildingStrategy {
        return nodes[0].blockBuildingStrategy(0) as SmartOnDemandBlockBuildingStrategy
    }

    fun doAndBuildBlocks(txBuilder: TransactionBuilder, nBlocks: Int = 1) {
        txBuilder.post()
        buildAndAwaitBlocks(nBlocks)
    }

    private fun fromConfig(config: PostchainClientConfig): PostchainClient {
        return PostchainClientProviderImpl().createClient(config)
    }

    private fun cliConf(keyIndex: Int, blockchainRid: BlockchainRid? = null): PostchainClientConfig {
        return PostchainClientConfig(
                blockchainRid ?: nodes[0].getBlockchainRid(0)!!,
                EndpointPool.singleUrl("http://127.0.0.1:" + nodes[0].getRestApiHttpPort()),
                listOf(
                        KeyPair.of(KeyPairHelper.pubKeyHex(keyIndex), KeyPairHelper.privKeyHex(keyIndex))
                )
        )
    }

    protected fun run(configGtv: Gtv) {
        val blockchainSetups = listOf(BlockchainSetupFactory.buildFromGtv(0, configGtv))
        val systemSetup = SystemSetupFactory.buildSystemSetup(blockchainSetups)
        systemSetup.nodeConfProvider = "net.postchain.devtools.utils.configuration.TestNodeConfigurationProvider" // "managed" not implemented yet. See NodeConfigurationProviderGenerator
        systemSetup.confInfrastructure = "net.postchain.managed.ManagedEBFTInfrastructureFactory"
        systemSetup.chainConfProvider = "managed"
        systemSetup.needRestApi = true

        createNodesFromSystemSetup(systemSetup, true)
    }
}
