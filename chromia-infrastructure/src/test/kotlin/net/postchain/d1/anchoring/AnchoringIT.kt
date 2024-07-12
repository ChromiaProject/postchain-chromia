package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.EContext
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BLOCK_HEADER
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.getClusterAnchoringChainConfig
import net.postchain.d1.getSystemAnchoringChainConfig
import net.postchain.debug.DiagnosticProperty
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.currentHeight
import net.postchain.devtools.getModules
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.util.postgres.PostgresDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

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
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")

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
        val topicHeaderData = TopicHeaderData.fromGtv(anchorHeaderExtra["G_my-topic"]!!)

        assertEquals(-1L, topicHeaderData.previousBlockHeight)

        val dappBlockQueries = getChainNodes(dappChain)[0].getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
        val dappBlockRids = (0..3).map { height -> gtv(dappBlockQueries.getBlockRid(height.toLong()).get()!!) }
        val anchorHash = gtv(dappBlockRids).merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        assertEquals(anchorHash.wrap(), topicHeaderData.hash.wrap())

        val blockchainRidColumn = field("blockchain_rid", PostgresDataType.BYTEA)
        val blockHeightColumn = field("block_height", PostgresDataType.BIGINT)
        withReadConnection(getChainNodes(anchorChain)[0].postchainContext.blockBuilderStorage, anchorChain) {
            val db = DatabaseAccess.of(it)

            val jooq = DSL.using(it.conn, SQLDialect.POSTGRES)
            val res = jooq.select(blockchainRidColumn, blockHeightColumn)
                    .from(table(db.tableName(it, "anchor_block")))
                    .fetch()

            assertEquals(4, res.size)
            assertEquals(blockchainRID.data.wrap(), res[0][blockchainRidColumn].wrap())
            assertEquals(0L, res[0][blockHeightColumn])
            assertEquals(blockchainRID.data.wrap(), res[1][blockchainRidColumn].wrap())
            assertEquals(1L, res[1][blockHeightColumn])
            assertEquals(blockchainRID.data.wrap(), res[2][blockchainRidColumn].wrap())
            assertEquals(2L, res[2][blockHeightColumn])
            assertEquals(blockchainRID.data.wrap(), res[3][blockchainRidColumn].wrap())
            assertEquals(3L, res[3][blockHeightColumn])

            val headers =
                    query(
                            getChainNodes(anchorChain)[0],
                            it,
                            "icmf_get_headers_with_messages_after_height",
                            gtv(
                                    mapOf(
                                            "topic" to gtv("G_my-topic"),
                                            "from_anchor_height" to gtv(-1)
                                    )
                            ),
                            anchorChain
                    ).asArray()
            assertEquals(4, headers.size)
            headers.forEachIndexed { index, header ->
                val rawHeader = header["block_header"]!!.asByteArray()
                val decodedHeader = BlockHeaderData.fromBinary(rawHeader)
                assertEquals(blockchainRID.data.wrap(), decodedHeader.getBlockchainRid().wrap())
                assertEquals(index.toLong(), decodedHeader.getHeight())
                assertEquals(messagesHash.wrap(), decodedHeader.getExtra()["icmf_send"]!!["G_my-topic"]!!["hash"]!!.asByteArray().wrap())

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
        withReadConnection(getChainNodes(anchorChain)[0].postchainContext.blockBuilderStorage, anchorChain) {
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
    fun verifyLastClusterAnchoredHeight() {
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")

        val dappChain = startDappChain()

        // --------------------
        // Dapp chain: Build 4 blocks
        // --------------------
        for (height in 0..3) {
            buildBlock(dappChain, height.toLong())
        }

        val blockchainRID: BlockchainRid = ChainUtil.ridOf(dappChain)

        // --------------------
        // Anchor chain: Build first anchor block
        // --------------------

        buildBlock(anchorChain, 0)

        // --------------------
        // Anchor chain: Actual test
        // --------------------
        val expectedLastAnchoredHeight = mutableMapOf(blockchainRID to AnchoringChainCheck(3L, true))
        verifyLastCusterAnchoredHeight(anchorChain, 0, expectedLastAnchoredHeight)
        verifyLastCusterAnchoredHeight(anchorChain, 1, expectedLastAnchoredHeight)
        verifyLastCusterAnchoredHeight(anchorChain, 2, expectedLastAnchoredHeight)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun verifyNodeWithDappChainWithLowerHeightThanLastClusterAnchoredHeight() {
        startManagedSystem(4, 0)
        val peerInfoMap = nodes.first().postchainContext.nodeConfigProvider.getConfiguration().peerInfoMap
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")

        val dappChain = startDappChain()

        buildBlock(dappChain, 0)
        buildBlock(anchorChain, 0)

        // Stop dapp chain on node 3 so we don't load the new block
        nodes[3].stopBlockchain(dappChain)
        buildBlock(listOf(nodes[0], nodes[1], nodes[2]), dappChain, 1)
        buildBlock(anchorChain, 1)

        // put node 2 in forced read-only mode (yes this is a hack to simulate behind scenario)
        nodes[3].shutdown()
        configOverrides.setProperty("readOnly", true)
        startOldNode(3, peerInfoMap, ChainUtil.ridOf(0))
        nodes[3].startBlockchain(anchorChain)
        nodes[3].startBlockchain(dappChain)
        await.pollInterval(Duration.ONE_SECOND).timeout(Duration.ONE_MINUTE).untilAsserted {
            assertEquals(nodes[3].currentHeight(anchorChain), 1)
            assertEquals(nodes[3].currentHeight(dappChain), 0)
        }

        val blockchainRID: BlockchainRid = ChainUtil.ridOf(dappChain)

        val expectedLastAnchoredHeight = mutableMapOf(blockchainRID to AnchoringChainCheck(1L, true))
        verifyLastCusterAnchoredHeight(anchorChain, 0, expectedLastAnchoredHeight)
        verifyLastCusterAnchoredHeight(anchorChain, 1, expectedLastAnchoredHeight)
        verifyLastCusterAnchoredHeight(anchorChain, 2, expectedLastAnchoredHeight)
        verifyLastCusterAnchoredHeight(anchorChain, 3, mutableMapOf(blockchainRID to AnchoringChainCheck(error = "Blockchain is behind CAC last anchored block.")))
    }

    private fun verifyLastCusterAnchoredHeight(anchorChain: Long, node: Int, expected: MutableMap<BlockchainRid, AnchoringChainCheck>) {
        await.pollInterval(Duration.ONE_SECOND).timeout(Duration.ONE_MINUTE).untilAsserted {
            assertTrue(getChainNodes(anchorChain)[node].postchainContext.nodeDiagnosticContext.get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK)?.value?.equals(expected)
                    ?: false)
        }
    }

    /**
     * Simple happy test to see that we can run 3 nodes to anchor a normal chain even when we hit the maxBlocksPerChain limit
     */
    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `happy anchoring with maxBlocksPerChain limit reached`() {
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring-max_blocks_per_chain.xml")
        val blocks = 7L

        val dappChain = startDappChain()

        buildBlock(dappChain, blocks - 1)

        // --------------------
        // Anchor chain: Check that we begin with nothing
        // --------------------
        val anchorBlockQueries = getChainNodes(anchorChain)[0].getBlockchainInstance(anchorChain).blockchainEngine.getBlockQueries()

        val expectedNumberOfTxs = 1  // Only the first TX
        val maxBlocksPerChain = 3 // Same as config file
        val anchoringBlocksNeeded = blocks / maxBlocksPerChain + 1

        // --------------------
        // Build and expect the following anchoring distribution:
        // Anchoring block 0: contains dapp block 0-2
        // Anchoring block 1: contains dapp block 3-5
        // Anchoring block 2: contains dapp block 6
        // --------------------
        for (anchorHeight in 0L until anchoringBlocksNeeded) {

            buildBlock(anchorChain, anchorHeight)

            val blockDataFull = anchorBlockQueries.getBlockAtHeight(anchorHeight).get()!!
            assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)
            val blockHeaderData = BlockHeaderData.fromBinary(blockDataFull.header.rawData)
            val anchorHeaderExtra = blockHeaderData.getExtra()[ICMF_ANCHOR_HEADERS_EXTRA]!!
            val topicHeaderData = TopicHeaderData.fromGtv(anchorHeaderExtra["G_my-topic"]!!)

            assertEquals(anchorHeight - 1, topicHeaderData.previousBlockHeight)

            val fromHeight = anchorHeight * maxBlocksPerChain
            val toHeight = (anchorHeight * maxBlocksPerChain + maxBlocksPerChain - 1).coerceAtMost(blocks - 1)

            val dappBlockQueries = getChainNodes(dappChain)[0].getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
            val dappBlockRids = (fromHeight..toHeight).map { height -> gtv(dappBlockQueries.getBlockRid(height).get()!!) }
            val anchorHash = gtv(dappBlockRids).merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            assertEquals(anchorHash.wrap(), topicHeaderData.hash.wrap())
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun onlyClusterChainsAreAnchored() {
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")

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
        startManagedSystem(4, 0)
        val systemAnchoringChain = startSystemAnchoringChain()
        val clusterAnchoringChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")

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

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun capSizeOfAnchoringTransactionOnMaxBlockSize() {
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring_limit_size.xml")

        verifyCap(anchorChain)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun capSizeOfAnchoringTransactionOnMaxTxSize() {
        startManagedSystem(4, 0)
        val anchorChain = startClusterAnchoringChain("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring_limit_size_on_tx.xml")

        verifyCap(anchorChain)
    }

    private fun verifyCap(anchorChain: Long) {
        val dappChain = startDappChain()
        // From blockchain_config_2_cluster_anchoring_limit_size.xml and blockchain_config_2_cluster_anchoring_limit_size_on_tx.xml
        val capSize = 120000

        val blockQueries = nodes.first().getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()

        var cutOffBlock = -1
        var currentSize = TX_SIZE_MARGIN
        for (height in 0..31) {
            buildBlock(dappChain, height.toLong())

            val blockHeaderAndWitness = blockQueries.getBlockAtHeight(height.toLong()).get()!!
            currentSize += OP_BLOCK_HEADER.length +
                    blockHeaderAndWitness.header.blockRID.size +
                    blockHeaderAndWitness.header.rawData.size +
                    blockHeaderAndWitness.witness.getRawData().size +
                    GTX_OP_OVERHEAD
            if (cutOffBlock == -1 && currentSize > capSize) {
                cutOffBlock = height
            }
        }

        val anchorBlockQueries = getChainNodes(anchorChain)[0].getBlockchainInstance(anchorChain).blockchainEngine.getBlockQueries()

        val heightZero = 0
        buildBlock(anchorChain, 0)

        val expectedNumberOfTxs = 1  // Only the first TX

        val blockDataFull = anchorBlockQueries.getBlockAtHeight(heightZero.toLong()).get()!!
        assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)

        val blockchainRidColumn = field("blockchain_rid", PostgresDataType.BYTEA)
        val blockHeightColumn = field("block_height", PostgresDataType.BIGINT)

        withReadConnection(getChainNodes(anchorChain)[0].postchainContext.blockBuilderStorage, anchorChain) {
            val db = DatabaseAccess.of(it)

            val jooq = DSL.using(it.conn, SQLDialect.POSTGRES)
            val res = jooq.select(blockchainRidColumn, blockHeightColumn)
                    .from(table(db.tableName(it, "anchor_block")))
                    .fetch()

            assertEquals(cutOffBlock, res.size)
        }

        buildBlock(anchorChain, 1)
        withReadConnection(getChainNodes(anchorChain)[0].postchainContext.blockBuilderStorage, anchorChain) {
            val db = DatabaseAccess.of(it)

            val jooq = DSL.using(it.conn, SQLDialect.POSTGRES)
            val res = jooq.select(blockchainRidColumn, blockHeightColumn)
                    .from(table(db.tableName(it, "anchor_block")))
                    .fetch()

            assertEquals(32, res.size)
        }
    }

    private fun startDappChain(): Long {
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/anchoring/blockchain_config_1.xml")!!.readText())

        return startNewBlockchain(setOf(0, 1, 2, 3), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig))
    }

    private fun startClusterAnchoringChain(blockchainConfigFile: String): Long {
        val anchorGtvConfig = getClusterAnchoringChainConfig(blockchainConfigFile)
        return startNewBlockchain(setOf(0, 1, 2, 3), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))
    }

    private fun startSystemAnchoringChain(): Long {
        val anchorGtvConfig = getSystemAnchoringChainConfig()
        return startNewBlockchain(setOf(0, 1, 2, 3), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))
    }

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", D1TestInfrastructureFactory::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("clusterManagementMock", AnchoringTestClusterManagement::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("anchoring-check.cluster-anchor-check-interval-ms", 1000L)
        nodeSetup.nodeSpecificConfigs.setProperty("anchoring-check.system-anchor-check-interval-ms", 1000L)
        nodeSetup.nodeSpecificConfigs.setProperty("anchoring-check.evm-anchor-check-interval-ms", -1)
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, name: String, args: Gtv, anchorChainId: Long): Gtv =
            node.getModules(anchorChainId).find { it.javaClass.simpleName.startsWith("Rell") }!!
                    .query(ctxt, name, args)
}