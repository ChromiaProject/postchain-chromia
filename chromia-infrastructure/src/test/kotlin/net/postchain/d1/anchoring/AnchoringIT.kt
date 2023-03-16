package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.core.EContext
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.getClusterAnchoringChainConfig
import net.postchain.d1.getSystemAnchoringChainConfig
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.util.postgres.PostgresDataType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Main idea is to have one "source" blockchain that generates block, so that these blocks can be anchored by another
 * "anchor" blockchain. If this work the golden path of anchoring should work.
 *
 * It doesn't matter what the "source" blocks contain.
 * Produce blocks containing Special transactions using the simplest possible setup, but as a minimum we need a new
 * custom test module to give us the "__xxx" operations needed.
 */
class AnchoringIT : ManagedModeTest() {
    companion object {
        val messagesHash = ByteArray(32) { i -> i.toByte() }
    }

    /**
     * Simple happy test to see that we can run 3 nodes with:
     * - a normal chain and
     * - an anchor chain.
     */
    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun happyAnchor() {
        startManagedSystem(3, 0)
        val anchorChain = startClusterAnchoringChain()

        val dappChain = startDappChain()

        // --------------------
        // Dapp chain: Build 4 blocks
        // --------------------
        for (height in 0..3) {
            buildBlock(dappChain, height.toLong())
        }

        val blockchainRID: BlockchainRid = ChainUtil.ridOf(dappChain)

        // --------------------
        // Anchor chain: Check that we begin with nothing
        // --------------------
        val anchorBlockQueries = getChainNodes(anchorChain)[0].getBlockchainInstance(anchorChain).blockchainEngine.getBlockQueries()

        // --------------------
        // Anchor chain: Build first anchor block
        // --------------------

        val heightZero = 0
        buildBlock(anchorChain, 0)

        // --------------------
        // Anchor chain: Actual test
        // --------------------
        val expectedNumberOfTxs = 1  // Only the first TX

        val blockDataFull = anchorBlockQueries.getBlockAtHeight(heightZero.toLong()).get()!!
        assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)
        val blockHeaderData = BlockHeaderData.fromBinary(blockDataFull.header.rawData)
        val anchorHeaderExtra = blockHeaderData.getExtra()[ICMF_ANCHOR_HEADERS_EXTRA]!!
        val topicHeaderData = TopicHeaderData.fromGtv(anchorHeaderExtra["my-topic"]!!)

        assertEquals(-1L, topicHeaderData.previousBlockHeight)

        val dappBlockQueries = getChainNodes(dappChain)[0].getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
        val dappBlockRids = (0..3).map { height -> gtv(dappBlockQueries.getBlockRid(height.toLong()).get()!!) }
        val anchorHash = gtv(dappBlockRids).merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        assertContentEquals(anchorHash, topicHeaderData.hash)

        val blockchainRidColumn = field("blockchain_rid", PostgresDataType.BYTEA)
        val blockHeightColumn = field("block_height", PostgresDataType.BIGINT)
        withReadConnection(getChainNodes(anchorChain)[0].storage, anchorChain) {
            val db = DatabaseAccess.of(it)

            val jooq = DSL.using(it.conn, SQLDialect.POSTGRES)
            val res = jooq.select(blockchainRidColumn, blockHeightColumn)
                    .from(table(db.tableName(it, "anchor_block")))
                    .fetch()

            assertEquals(4, res.size)
            assertContentEquals(blockchainRID.data, res[0][blockchainRidColumn])
            assertEquals(0L, res[0][blockHeightColumn])
            assertContentEquals(blockchainRID.data, res[1][blockchainRidColumn])
            assertEquals(1L, res[1][blockHeightColumn])
            assertContentEquals(blockchainRID.data, res[2][blockchainRidColumn])
            assertEquals(2L, res[2][blockHeightColumn])
            assertContentEquals(blockchainRID.data, res[3][blockchainRidColumn])
            assertEquals(3L, res[3][blockHeightColumn])

            val headers =
                    query(
                            getChainNodes(anchorChain)[0],
                            it,
                            "icmf_get_headers_with_messages_after_height",
                            gtv(
                                    mapOf(
                                            "topic" to gtv("my-topic"),
                                            "from_anchor_height" to gtv(-1)
                                    )
                            ),
                            anchorChain
                    ).asArray()
            assertEquals(4, headers.size)
            headers.forEachIndexed { index, header ->
                val rawHeader = header["block_header"]!!.asByteArray()
                val decodedHeader = BlockHeaderData.fromBinary(rawHeader)
                assertContentEquals(blockchainRID.data, decodedHeader.getBlockchainRid())
                assertEquals(index.toLong(), decodedHeader.getHeight())
                assertContentEquals(messagesHash, decodedHeader.getExtra()["icmf_send"]!!["my-topic"]!!["hash"]!!.asByteArray())

                val witness = BaseBlockWitness.fromBytes(header["witness"]!!.asByteArray())
                val digest = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
                witness.getSignatures().forEach { signature ->
                    assertTrue(cryptoSystem.verifyDigest(digest, signature))
                }
            }
        }

        // restart anchoring chain
        nodes.forEach {
            it.stopBlockchain(anchorChain)
            it.startBlockchain(anchorChain)
        }

        // build another block and verify it is anchored
        buildBlock(dappChain, 4)
        buildBlock(anchorChain, 1)
        withReadConnection(getChainNodes(anchorChain)[0].storage, anchorChain) {
            val db = DatabaseAccess.of(it)

            val jooq = DSL.using(it.conn, SQLDialect.POSTGRES)
            val res = jooq.select(blockchainRidColumn, blockHeightColumn)
                    .from(table(db.tableName(it, "anchor_block")))
                    .fetch()

            assertEquals(5, res.size)
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun onlyClusterChainsAreAnchored() {
        startManagedSystem(3, 0)
        val anchorChain = startClusterAnchoringChain()

        val dappChain = startDappChain()

        // Add an extra dapp chain that will get chainId == 3, do string replacement to make it unique
        // This dapp will be mocked to be in another cluster
        val dapp2GtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/anchoring/blockchain_config_1.xml")!!.readText().replace("NOT_USED", "NOT_USED2")
        )

        val dapp2Chain = startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dapp2GtvConfig))

        // Build one block each on the dapp chains
        buildBlock(dappChain, 0L)
        buildBlock(dapp2Chain, 0L)

        // Build a block on the anchor chain
        buildBlock(anchorChain, 0)

        // Ensure only the block from dapp1 was anchored
        val anchorBlockQueries = getChainNodes(anchorChain)[0].blockQueries(anchorChain)
        val dappBlock = anchorBlockQueries.query("get_last_anchored_block", gtv(mapOf("blockchain_rid" to gtv(ChainUtil.ridOf(dappChain))))).get()
        assertFalse(dappBlock.isNull())

        val dapp2Block = anchorBlockQueries.query("get_last_anchored_block", gtv(mapOf("blockchain_rid" to gtv(ChainUtil.ridOf(dapp2Chain))))).get()
        assertTrue(dapp2Block.isNull())
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun systemAnchoringAnchorsClusterAnchoringBlocks() {
        startManagedSystem(3, 0)
        val systemAnchoringChain = startSystemAnchoringChain()
        val clusterAnchoringChain = startClusterAnchoringChain()

        buildBlock(clusterAnchoringChain, 0L)
        buildBlock(systemAnchoringChain, 0L)

        // Verify that system anchoring chain has anchored the block that was built on cluster anchoring chain
        val systemAnchoringBlockQueries = getChainNodes(systemAnchoringChain)[0].blockQueries(systemAnchoringChain)
        val clusterAnchoringBlock = systemAnchoringBlockQueries.query("get_last_anchored_block", gtv(mapOf("blockchain_rid" to gtv(ChainUtil.ridOf(clusterAnchoringChain))))).get()
        assertFalse(clusterAnchoringBlock.isNull())

        buildBlock(clusterAnchoringChain, 1L)
        // Verify that cluster anchoring chain has not anchored the block that was built on system anchoring chain
        val clusterAnchoringBlockQueries = getChainNodes(clusterAnchoringChain)[0].blockQueries(clusterAnchoringChain)
        val systemAnchoringBlock = clusterAnchoringBlockQueries.query("get_last_anchored_block", gtv(mapOf("blockchain_rid" to gtv(ChainUtil.ridOf(systemAnchoringChain))))).get()
        assertTrue(systemAnchoringBlock.isNull())
    }

    private fun startDappChain(): Long {
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/anchoring/blockchain_config_1.xml")!!.readText())

        return startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig))
    }

    private fun startClusterAnchoringChain(): Long {
        val anchorGtvConfig = getClusterAnchoringChainConfig()
        return startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))
    }

    private fun startSystemAnchoringChain(): Long {
        val anchorGtvConfig = getSystemAnchoringChainConfig()
        return startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))
    }

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", D1TestInfrastructureFactory::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("clusterManagementMock", AnchoringTestClusterManagement::class.qualifiedName)
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, name: String, args: Gtv, anchorChainId: Long): Gtv =
            node.getModules(anchorChainId).find { it.javaClass.simpleName.startsWith("Rell") }!!
                    .query(ctxt, name, args)
}