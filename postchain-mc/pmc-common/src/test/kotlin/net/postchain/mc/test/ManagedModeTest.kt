package net.postchain.mc.test

import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import net.postchain.chain0.common.addBlockchainReplicaOperation
import net.postchain.chain0.common.queries.GetNodesWithProviderResult
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClient
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
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
        val anchorConfigXmlFile = getFileFromClasspath("/net/postchain/mc/test/config/blockchain_config_anchor.xml")

        private fun getFileFromClasspath(path: String): File {
            val tempFile = File.createTempFile("managed-mode-test", "")
            val inputStream = Companion::class.java.getResourceAsStream(path)!!
            tempFile.writeBytes(inputStream.readAllBytes())
            return tempFile
        }
    }

    protected fun getPostchainClient(appConfig: PostchainClientConfig): PostchainClient {
        return ConcretePostchainClientProvider().createClient(appConfig)
    }

    abstract fun cliExecution(cliConfig: PostchainClientConfig): CliExecution

    protected fun assertProviderData(provPubkey: String, name: String, isActive: Boolean?) {
        val data = provExecutor.getProviderInfo(provPubkey)
        assertArrayEquals(data.pubkey.data, provPubkey.hexStringToByteArray())
        assertk.assert(data.name).isEqualTo(name)
        assertk.assert(data.active).isEqualTo(isActive)
    }

    protected fun assertProviderEnabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey)
        assertk.assert(data.active).isEqualTo(true)
    }

    protected fun assertProviderDisabled(providerPublicKey: String) {
        val data = provExecutor.getProviderInfo(providerPublicKey)
        assertk.assert(data.active).isEqualTo(false)
        val listReplicas = provExecutor.listBlockchainReplicas(clientConfig.blockchainRid.toHex())
        assertEquals(0, listReplicas.size)

        val listSigners = provExecutor.listBlockchainSigners(clientConfig.blockchainRid.toHex())
        assertEquals(1, listSigners.size)
    }

    protected fun assertNextConfiguration(config: PostchainClientConfig, expectedHeight: Long): ByteArray {

        // Get next configuration height of new blockchain configuration
        val client = getPostchainClient(config)
        val height = client.nmFindNextConfigurationHeight(config.blockchainRid, 0)
        assertk.assert(height).isEqualTo(expectedHeight)

        // Get next configuration
        val bc = client.nmGetBlockchainConfiguration(config.blockchainRid, height!!)
        assertk.assert(bc).isNotNull()
        return bc!!
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
        val vs = client.querySync(opName, GtvFactory.gtv(keyName to addedItem))
        assertk.assert(vs.asInteger()).isGreaterThan(0L)
    }

    fun assertAddedNode(providerPublicKey: String, nodePubkey: String, host: String, port: Long, cluster: String) {
        awaitUntilAsserted {
            var nodeInfo = provExecutor.getNodeInfo(nodePubkey).asDict()
            assertk.assert(nodeInfo["active"]?.asBoolean()).isEqualTo(true)
            assertk.assert(nodeInfo["host"]?.asString()).isEqualTo(host)
            assertk.assert(nodeInfo["port"]?.asInteger()).isEqualTo(port)
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

        val replicas = provExecutor.listBlockchainReplicas(clientConfig.blockchainRid.toHex())
        assertEquals(1, replicas.size)
    }

//    // nodelist: comma-separated list of node pubkeys

    fun awaitBlockchainReload() {
//        Awaitility.await().atMost(Duration.ONE_MINUTE)
//                .untilAsserted {
//                    assertk.assert(nodes[0].getModules(0L)).isNotEmpty()
//                    assertk.assert(nodes[0].getModules(0L).first())
//                            .isInstanceOf(ManagedTestModuleReconfiguring2::class)
//                }
//        buildAnd
        Thread.sleep(2000)
    }

    protected fun assertBlockchainReplica(clientConfig: PostchainClientConfig, nodePubkey: String, host: String, port: Long) {
        val executor = cliExecution(clientConfig)
        val listReplicas = executor.listBlockchainReplicas(clientConfig.blockchainRid.toHex())
        assertEquals(1, listReplicas.size)
        val bc = listReplicas[0]
        assertEquals(nodePubkey, bc[0].asByteArray().toHex())
        assertEquals(host, bc[1].asString())
        assertEquals(port, bc[2].asInteger())
        assertTrue(bc[3].asBoolean())
    }

    private fun assertNodeInfo(
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

    protected fun assertListNodes() {
        val providerNodes = provExecutor.listNodesByProvider(provConfig.pubkey())
        assertEquals(2, providerNodes.size)

        val nodeList = provExecutor.listNodesWithProvider()
        assertNodeInfo(nodeList[0], node0Host, node0Port, nodes[0].pubKey, provConfig.pubkey(), true)
        assertNodeInfo(nodeList[1], node1Host, node1Port, node1Pubkey, provConfig.pubkey(), true)
    }

    protected fun buildAndAwaitBlocks(nBlocks: Int) {
        val strat = strategy()
        val height = strat.committedHeight + nBlocks
        strat.buildBlocksUpTo(height.toLong())
        strat.awaitCommitted(height)
        awaitBlockchainReload()
    }

    private fun strategy(): SmartOnDemandBlockBuildingStrategy {
        val strat = nodes[0].blockBuildingStrategy(0) as SmartOnDemandBlockBuildingStrategy
        return strat
    }

    fun doAndBuildBlocks(clientConfig: PostchainClientConfig, txBuilder: TransactionBuilder, nBlocks: Int = 1) {
        val executor = cliExecution(clientConfig)
        executor.sendTxUnconfirmed(txBuilder)
        buildAndAwaitBlocks(nBlocks)
    }

}