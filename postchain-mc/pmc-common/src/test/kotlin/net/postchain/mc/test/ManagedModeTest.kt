package net.postchain.mc.test

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
import net.postchain.chain0.common.registerNodeOperation
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.nm_api.nmGetPendingBlockchainConfiguration
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.impl.PostchainClientProviderImpl
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.pubkey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun PostchainClientConfig.pubkey() = signers.first().pubKey.hex()
fun PostchainClientConfig.privkey() = signers.first().privKey

abstract class ManagedModeTest : RellIntegrationTest() {

    val node2Pubkey = KeyPairHelper.pubKeyHex(2)
    val node2Host = "127.0.0.1"
    val node2Port = 9872L

    val node1Pubkey = KeyPairHelper.pubKeyHex(1)
    val node1Host = "127.0.0.1"
    val node1Port = 9871L

    val node0Host = "127.0.0.1"
    val node0Port = 9870L
    val node0BlockSignerKey = 0

    val adminKey = 10
    val providerKey = 11
    val providerKey2 = 12
    val clientConfig by lazy { cliConf(adminKey) }
    val provConfig by lazy { cliConf(providerKey) }
    val prov2Config by lazy { cliConf(providerKey2) }
    open val provClient by lazy { ClientUtil.fromConfig(provConfig) }
    open val prov2Client by lazy { ClientUtil.fromConfig(prov2Config) }
    lateinit var blockchain0ConfigGtv: Gtv

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

    protected fun getPostchainClient(appConfig: PostchainClientConfig): PostchainClient {
        return PostchainClientProviderImpl().createClient(appConfig)
    }

    protected fun assertProviderData(provPubkey: String, name: String, isActive: Boolean?) {
        val data = provClient.getProviderData(PubKey(provPubkey))
        assertArrayEquals(data.pubkey.data, provPubkey.hexStringToByteArray())
        assert(data.name).isEqualTo(name)
        assert(data.active).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(providerPublicKey: String) {
        val data = provClient.getProviderData(PubKey(providerPublicKey))
        assert(data.active).isEqualTo(true)
    }

    protected fun assertProviderDisabled(providerPublicKey: String) {
        val data = provClient.getProviderData(PubKey(providerPublicKey))
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
                host, port, "",
                if (clusterName == "") listOf() else listOf(clusterName)
        ).post()
        buildAndAwaitBlocks(1)
        assertAddedNode(client.config.pubkey().hex(), key, host, port, clusterName)
    }

    fun assertAdded(opName: String, keyName: String, addedItem: Gtv) {
        val client = getPostchainClient(provConfig)
        val vs = client.query(opName, GtvFactory.gtv(keyName to addedItem))
        assert(vs.asInteger()).isGreaterThan(0L)
    }

    private fun assertAddedNode(providerPublicKey: String, nodePubkey: String, host: String, port: Long, cluster: String) {
        awaitUntilAsserted {
            val nodePK = PubKey(nodePubkey)
            val nodeData = provClient.getNodeData(nodePK)
            assert(nodeData.active).isEqualTo(true)
            assert(nodeData.host).isEqualTo(host)
            assert(nodeData.port).isEqualTo(port)
            assertEquals(nodeData.provider, providerPublicKey.hexStringToWrappedByteArray())
            assertEquals(nodeData.pubkey, nodePK.wData)
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
            nodePubkey: String,
            providerPubkey: String,
            b: Boolean
    ) {
        assertEquals(nodeHost, n.host)
        assertEquals(nodePort, n.port)
        assertEquals(nodePubkey, n.pubkey.toHex())

        assertEquals(providerPubkey, n.provider.toHex())
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
}