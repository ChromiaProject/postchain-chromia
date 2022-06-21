package net.postchain.images.enterprise0

import assertk.assert
import assertk.assertions.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.dapp.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.initialProviderPubKey
import net.postchain.postgres.ChainDatabaseCommunicator
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import org.junit.jupiter.api.*
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

/**
 * This test proves the functionality of enterprise0 dapp and the minimal needed configuration.
 *
 * We only use a single provider, which is configured in run.xml to be the initial provider and [adminPubKey].
 * This means that a single provider owns the entire network and is done to keep the voting steps simple.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class Enterprise0ExampleIT {


    companion object: ManagedModeBase("enterprise0-example", "/enterprise0/rell", "/chain_zero/run-enterprise0.xml") {

        @JvmStatic
        @BeforeAll
        fun setup() {
            startNodesAndChain0()
        }

        @JvmStatic
        @AfterAll
        fun breakdown() {
            stopNodes()
        }
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        node1Db.awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider 1`() {
        node1.txAsAdmin(brid, "init")
        node1Db.awaitNewBlock()
        assert(node1.client(brid).querySync("get_all_providers").asArray().size).isEqualTo(1)
    }

    @Test
    @Order(3)
    fun `Add node 1 to its own network`() {
        consoleLogger.info { "Adding node 1 to its own network" }
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(initialProviderPubKey),
            gtv(node1.pubKey.hexStringToByteArray()),
            gtv(node1.nodeHost),
            gtv(node1.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        assert(
            node1.client(brid).query("is_node", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
                .asBoolean()
        ).isTrue()
        val nodeGtv =
            node1.client(brid).query("get_node_data", gtv("pubkey" to gtv(node1.pubKey.hexStringToByteArray()))).get()
        assert(nodeGtv.asDict()["active"]!!.asInteger()).isEqualTo(1L)
    }

    @Test
    @Order(4)
    fun `Make chain0 aware of itself`() {
        node1.txAsAdmin(
            brid, "propose_blockchain", gtv(initialProviderPubKey), gtv(chain0Config.readBytes()), gtv(
                listOf( gtv( node1.pubKeyByteArray ) )
            )
        )
        val addChain0Proposal =
            node1.client(brid).querySync("get_proposals_since", gtv("since" to gtv(0))).asArray().first()
        node1.txAsAdmin(brid, "make_vote", gtv(initialProviderPubKey), addChain0Proposal.asDict()["rowid"]!!, gtv(true))

        assert(node1.client(brid).querySync("get_all_blockchains").asArray().size).isEqualTo(1)
    }

    @Test
    @Order(5)
    fun `Make node 2 and 3 signers of c0`() {
        consoleLogger.info { "Adding node2 and node3 to node1" }
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(initialProviderPubKey),
            gtv(node2.pubKey.hexStringToByteArray()),
            gtv(node2.nodeHost),
            gtv(node2.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        node1.txAsAdmin(
            brid,
            "add_node",
            gtv(initialProviderPubKey),
            gtv(node3.pubKey.hexStringToByteArray()),
            gtv(node3.nodeHost),
            gtv(node3.nodePort.toLong())
        )
        node1Db.awaitNewBlock()
        listOf(node2, node3).forEach { addedNode ->
            assert(
                node1.client(brid).query("is_node", gtv("pubkey" to gtv(addedNode.pubKey.hexStringToByteArray()))).get()
                    .asBoolean(),
                name = "Node ${addedNode.nodeHost} is added to ${node1.nodeHost}"
            ).isTrue()
        }
        val newSignerNodes = listOf(node2, node3).map { gtv(it.pubKeyByteArray) }
        node1.txAsAdmin(
            brid,
            "propose_add_blockchain_signers",
            gtv(initialProviderPubKey),
            gtv(brid.toHex()),
            gtv(newSignerNodes)
        )
        val addChain0Proposal =
            node1.client(brid).querySync("get_proposals_since", gtv("since" to gtv(0L))).asArray().first()
        node1.txAsAdmin(brid, "make_vote", gtv(initialProviderPubKey), addChain0Proposal.asDict()["rowid"]!!, gtv(true))
        // Adding signers will update the blockchain configuration after 5 blocks
        val heightWithSigners = node1Db.getHeight() + 5
        runAsync(node1Db, node2Db, node3Db) {
            it.awaitBlockHeight(heightWithSigners)
        }
        val c0 = node1.client(brid).querySync("get_blockchain", gtv("rid" to gtv(brid.toHex())))
        assert(node1.client(brid).getBlockChainSigners(c0).size).isEqualTo(3)
        assert(node2.client(brid).getBlockChainSigners(c0).size).isEqualTo(3)
        assert(node3.client(brid).getBlockChainSigners(c0).size).isEqualTo(3)
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Deployment of new Dapp tests")
    inner class DappDeployment {

        val dappId = 100L
        lateinit var node2DappDb: ChainDatabaseCommunicator
        val dappToBrid = mutableMapOf<Long, BlockchainRid>()

        @BeforeAll
        fun `Deploy test-dapp to the network`() {
            listOf(node1, node2, node3).forEach { node ->
                Assumptions.assumeTrue {
                    node.client(brid).querySync("get_all_blockchains", gtv(mapOf())).asArray().size == 1
                }
            }
            val applicationFolder = this::class.java.getResource("/$resourceFolder/dapp")!!
            val runConf = this::class.java.getResource("/$resourceFolder/dapp/run.xml")!!
            val rellConfig = RellRunConfigGenerator.generateCli(
                File(applicationFolder.toURI()),
                File(runConf.toURI()),
                RellVersions.VERSION,
                false
            ).apply {
                RellRunConfigGenerator.buildFiles(this.config)
            }

            val nodeGtvs = listOf(node1, node2, node3).map { gtv(it.pubKeyByteArray) }

            rellConfig.config.chains.forEach { chain ->
                consoleLogger.info { "Adding test dapp ${chain.iid}" }
                chain.configs.forEach { (height, chainHeightConfig) ->
                    consoleLogger.info { "On height $height" }
                    node2.txAsAdmin(
                        brid,
                        "propose_blockchain",
                        gtv(initialProviderPubKey),
                        gtv(GtvEncoder.encodeGtv(chainHeightConfig.gtvConfig)),
                        gtv(nodeGtvs)
                    )
                    val addChain0Proposal =
                        node2.client(brid).querySync("get_proposals_since", gtv("since" to gtv(0L))).asArray().first()
                    val txId = node1.txAsAdmin(
                        brid,
                        "make_vote",
                        gtv(initialProviderPubKey),
                        addChain0Proposal.asDict()["rowid"]!!,
                        gtv(true)
                    )
                    dappToBrid[chain.iid] = node2.client(brid)
                        .querySync("get_added_blockchain_rid", gtv("tx_rid" to gtv(txId.data)))
                        .asByteArray()
                        .let { BlockchainRid(it) }
                        .also { consoleLogger.info { "With blockchain ID ${it.toShortHex()}" } }
                }
            }
            val heightWithDappDeployed = node2Db.getHeight() + 1// Dapp is deployed and dapp db-table has been created
            runAsync(node1Db, node2Db, node3Db) {
                it.awaitBlockHeight(heightWithDappDeployed)
            }
            node2DappDb = postgres.createChainDatabaseCommunicator(dappId, node2.appConfig.databaseSchema)
                .apply { awaitBlockHeight(0) }
        }

        @Test
        @Order(1)
        fun `Dapp is deployed`() {
            listOf(node1, node2, node3).forEach { node ->
                assert(node.client(brid).querySync("get_all_blockchains", gtv(mapOf())).asArray().size).isEqualTo(2)
            }
        }

        @Test
        @Order(2)
        fun `Transactions can be sent to newly deployed dapp`() {
            Assumptions.assumeTrue(dappToBrid.containsKey(dappId))
            val testCity = "uppsala"
            node2.txAsAdmin(dappToBrid[dappId]!!, "add_city", gtv(testCity))
            node2DappDb.awaitNewBlock()
            listOf(node1, node2, node3).forEach { node ->
                assert(
                    node.client(dappToBrid[dappId]!!).query("get_cities", gtv(mapOf())).get().asArray()
                        .map { it.asString() })
                    .containsExactly(testCity)
            }
        }
    }

    private fun <T> runAsync(vararg obj: T, action: (T) -> Unit) {
        runBlocking {
            withContext(coroutineContext) {
                obj.asList().forEach {
                    launch { action(it) }
                }
            }
        }
    }
}

fun PostchainClient.getBlockChainSigners(blockChain: Gtv): Array<out Gtv> {
    return querySync("get_blockchain_signers", gtv("blockchain" to blockChain)).asArray()
}
