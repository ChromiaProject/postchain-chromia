package net.postchain.mc.test

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.base.configuration.KEY_QUEUE_CAPACITY
import net.postchain.chain0.common.addBlockchainReplicaOperation
import net.postchain.chain0.common.queries.GetNodesWithProviderResult
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.chain0.common.queries.getBlockchainReplicas
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.impl.PostchainClientProviderImpl
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.common0.CliExecution
import org.junit.jupiter.api.Assertions.assertArrayEquals
import java.io.File
import kotlin.test.assertEquals
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
    open val provExecutor by lazy { cliExecution(provConfig) }
    open val provClient by lazy { ClientUtil.fromConfig(provConfig) }
    open val prov2Executor by lazy { cliExecution(prov2Config) }
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

    abstract fun cliExecution(cliConfig: PostchainClientConfig): CliExecution

    protected fun assertProviderData(provPubkey: String, name: String, isActive: Boolean?) {
        val data = provExecutor.getProviderInfo(provPubkey)
        assertArrayEquals(data.pubkey.data, provPubkey.hexStringToByteArray())
        assert(data.name).isEqualTo(name)
        assert(data.active).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey)
        assert(data.active).isEqualTo(true)
    }

    protected fun assertProviderDisabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey)
        assert(data.active).isEqualTo(false)
        val listReplicas = provExecutor.getPostchainClient().getBlockchainReplicas(clientConfig.blockchainRid)
        assertEquals(0, listReplicas.size)

        val listSigners = provExecutor.getPostchainClient().getBlockchainSigners(clientConfig.blockchainRid)
        assertEquals(1, listSigners.size)
    }

    protected fun assertNextConfiguration(config: PostchainClientConfig, expectedHeight: Long, expectedQueueCapacity: Long? = null) {
        // Get next configuration height of new blockchain configuration
        val client = getPostchainClient(config)
        val lastHeight = client.getBlockchainLastHeight(config.blockchainRid)
        val actualHeight = client.nmFindNextConfigurationHeight(config.blockchainRid, lastHeight)
        assert(actualHeight).isEqualTo(expectedHeight)

        // Get next configuration
        val bc = client.nmGetBlockchainConfiguration(config.blockchainRid, actualHeight!!)
        assert(bc).isNotNull()

        if (expectedQueueCapacity != null) {
            val gtv = GtvDecoder.decodeGtv(bc!!)
            assertEquals(expectedQueueCapacity, gtv[KEY_QUEUE_CAPACITY]?.asInteger())
        }
    }

    /**
     * Add node; optionally also add it to a cluster
     */
    fun addNode(configProv: PostchainClientConfig, key: String, host: String, port: Long, clusterName: String) {
        val executor = cliExecution(configProv)
        executor.sendTxUnconfirmed(executor.addNodeAsync(key, host, port, "", clusterName))
        buildAndAwaitBlocks(1)
        assertAddedNode(configProv.pubkey(), key, host, port, clusterName)
    }

    /**
     * Add node0; optionally also add it to a cluster
     */
    fun addNode0(configProv: PostchainClientConfig, clusterName: String) {
        addNode(configProv, nodes[0].pubKey, node0Host, node0Port, clusterName)
    }

    fun assertAdded(opName: String, keyName: String, addedItem: Gtv) {
        val client = getPostchainClient(provConfig)
        val vs = client.query(opName, GtvFactory.gtv(keyName to addedItem))
        assert(vs.asInteger()).isGreaterThan(0L)
    }

    fun assertAddedNode(providerPublicKey: String, nodePubkey: String, host: String, port: Long, cluster: String) {
        awaitUntilAsserted {
            var nodeInfo = provExecutor.getNodeInfo(nodePubkey).asDict()
            assert(nodeInfo["active"]?.asBoolean()).isEqualTo(true)
            assert(nodeInfo["host"]?.asString()).isEqualTo(host)
            assert(nodeInfo["port"]?.asInteger()).isEqualTo(port)
            assertArrayEquals(nodeInfo["provider"]?.asByteArray(), providerPublicKey.hexStringToByteArray())
            assertArrayEquals(nodeInfo["pubkey"]?.asByteArray(), nodePubkey.hexStringToByteArray())
            if (cluster != "") {
                assertEquals(nodeInfo["cluster"]?.asArray()?.map { it.asString() }, listOf(cluster))
            }
        }
    }

    /** Initialization function that adds node0 with configuration from  config.properties. It also adds blockchain (This is
     * function addNode0AndBlockchain). Finally, node1 is added as replica for bc0.
     * */
    protected fun initAndNode1ReplicaOfBc0() {
        //add node1 (no specified cluster)
        addNode(provConfig, node1Pubkey, node1Host, node1Port, "")

        // make node 1 a replica for bc0
        val tx = provExecutor.getPostchainClient().transactionBuilder()
                .addBlockchainReplicaOperation(
                        provConfig.signers.first().pubKey.data,
                        clientConfig.blockchainRid,
                        node1Pubkey.hexStringToByteArray())
        provExecutor.sendTxUnconfirmed(tx)
        buildAndAwaitBlocks(5)

        val replicas = provExecutor.getPostchainClient().getBlockchainReplicas(clientConfig.blockchainRid)
        assertEquals(1, replicas.size)
    }

    protected fun assertBlockchainReplica(clientConfig: PostchainClientConfig, nodePubkey: String, host: String, port: Long) {
        val executor = cliExecution(clientConfig)
        val listReplicas = executor.getPostchainClient().getBlockchainReplicas(clientConfig.blockchainRid)
        assertEquals(1, listReplicas.size)
        val bc = listReplicas[0]
        assertEquals(nodePubkey, bc[0].asByteArray().toHex())
        assertEquals(host, bc[1].asString())
        assertEquals(port, bc[2].asInteger())
        assertTrue(bc[3].asBoolean())
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
        assertEquals(nodePubkey, n.pubkey.hex())

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

    fun doAndBuildBlocks(clientConfig: PostchainClientConfig, txBuilder: TransactionBuilder, nBlocks: Int = 1) {
        val executor = cliExecution(clientConfig)
        executor.sendTxUnconfirmed(txBuilder)
        buildAndAwaitBlocks(nBlocks)
    }

}