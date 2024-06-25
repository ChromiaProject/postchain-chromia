package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.base.data.MinimalBlockHeaderInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.ValidationResult
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.Validation
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

private const val GTX_OP_OVERHEAD = 20

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
open class AnchoringSpecialTxExtension(private val anchoringReceiverFactory: AnchoringReceiverFactory) : GTXSpecialTxExtension {

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    private val _relevantOps = setOf(OP_BLOCK_HEADER)

    lateinit var isSigner: () -> Boolean
    lateinit var anchoringReceiver: AnchoringReceiver
    lateinit var clusterManagement: ClusterManagement
    lateinit var blockchainConfigProvider: BlockchainConfigProvider
    lateinit var anchoringConfig: AnchoringBlockchainConfigData
    var maxTxSize: Long = -1

    /** This is for querying ourselves, i.e. the "anchoring Rell app" */
    private lateinit var module: GTXModule

    private lateinit var cryptoSystem: CryptoSystem

    override fun getRelevantOps() = _relevantOps

    override fun init(
            module: GTXModule,
            chainID: Long,
            blockchainRID: BlockchainRid,
            cs: CryptoSystem
    ) {
        this.module = module
        cryptoSystem = cs
    }

    fun createReceiver(anchoringBlockchainRid: BlockchainRid) {
        anchoringReceiver = anchoringReceiverFactory.create(clusterManagement, anchoringBlockchainRid)
    }

    /**
     * Asked Alex, and he said we always use "begin" for special TX (unless we are wrapping up something)
     * so we only add them here (if we have any).
     */
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = when (position) {
        SpecialTransactionPosition.Begin -> ::anchoringReceiver.isInitialized
        SpecialTransactionPosition.End -> false
    }

    /**
     * For Anchor chain we simply pull all the messages from all the cluster anchoring pipes and create operations.
     *
     * Since the Extension framework expects us to add a TX before and/or after the main data of a block,
     * we create ONE BIG tx with all operations in it (for the "before" position).
     * (In an anchor chain there will be no "normal" transactions, only this one big "before" special TX)
     *
     * @param position will always be "begin", we don't care about it here
     * @param bctx is the context of the anchor chain (but without BC RID)
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val pipes = anchoringReceiver.getRelevantPipes()
        var currentSize = 0

        // Extract all packages from all pipes
        val ops = mutableListOf<OpData>()
        pipeIt@ for (pipe in pipes) {
            var opsCount = 0
            var currentHeight: Long = getLastAnchoredHeight(bctx, pipe.blockchainRid)
            pipePacketsIt@ while (pipe.mightHaveNewPackets()) {
                val clusterAnchorPackets = pipe.fetchNextRange(currentHeight + 1, anchoringConfig.maxBlocksPerChain)
                if (clusterAnchorPackets.isEmpty()) {
                    break // Nothing more to find
                } else {
                    for (clusterAnchorPacket in clusterAnchorPackets) {
                        val (opData, size) = buildOpData(clusterAnchorPacket)
                        if (currentSize + size > maxTxSize - TX_SIZE_MARGIN) {
                            break@pipeIt
                        }
                        ops.add(opData)
                        opsCount++
                        currentSize += size

                        if (anchoringConfig.maxBlocksPerChain > 0 && opsCount + 1 > anchoringConfig.maxBlocksPerChain) {
                            break@pipePacketsIt
                        }
                    }
                    currentHeight += clusterAnchorPackets.size
                }
            }
        }
        return ops
    }

    open fun numberOfBlocksToAnchor(): Long = if (!::anchoringReceiver.isInitialized) 0 else
        anchoringReceiver.getRelevantPipes().sumOf { if (it.numberOfNewPackets() > 0) it.numberOfNewPackets() else 0 }

    private fun getLastAnchoredHeight(ctxt: EContext, blockchainRID: BlockchainRid): Long =
            getLastAnchoredBlock(ctxt, blockchainRID)?.height ?: -1

    /**
     * Transform to [AnchoringPacket] to [OpData] put arguments in correct order
     *
     * @param clusterAnchorPacket is what we get from pipe
     * @return the [OpData] we can use to create a special TX, and the size of it
     */
    internal fun buildOpData(clusterAnchorPacket: AnchoringPacket): Pair<OpData, Int> {
        val gtvHeader: Gtv = GtvDecoder.decodeGtv(clusterAnchorPacket.rawHeader)
        val gtvWitness = GtvByteArray(clusterAnchorPacket.rawWitness)

        return OpData(OP_BLOCK_HEADER, arrayOf(gtv(clusterAnchorPacket.blockRid), gtvHeader, gtvWitness)) to
                OP_BLOCK_HEADER.length +
                clusterAnchorPacket.blockRid.size +
                GtvEncoder.encodeGtv(gtvHeader).size +
                clusterAnchorPacket.rawWitness.size +
                GTX_OP_OVERHEAD
    }

    /**
     * We look at the content of all operations (to check if the block headers are ok and nothing is missing)
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        val chainHeadersMap = mutableMapOf<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>()
        val relevantChains = anchoringReceiver.getRelevantChains()

        for (op in ops) {
            val anchorOpData = AnchoringOpData.validateAndDecodeOpData(op) ?: return false

            val headerData = anchorOpData.headerData
            val bcRid = BlockchainRid(headerData.getBlockchainRid())
            if (isSigner() && bcRid !in relevantChains) {
                logger.warn("Blocks from blockchain $bcRid are not allowed to be anchored in this chain")
                return false
            }

            val blockRid = headerData.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            if (!blockRid.contentEquals(anchorOpData.blockRid)) {
                logger.warn("Invalid block-rid: ${anchorOpData.blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}, expected: ${blockRid.toHex()}")
                return false
            }

            val peers = try {
                blockchainConfigProvider.getRelevantPeers(headerData)
            } catch (e: UserMistake) {
                logger.warn(e.message)
                return false
            }

            try {
                val witness = BaseBlockWitness.fromBytes(anchorOpData.witness)
                Validation.validateBlockSignatures(cryptoSystem, headerData.getPreviousBlockRid(), GtvEncoder.encodeGtv(headerData.toGtv()), blockRid, peers, witness)
            } catch (e: UserMistake) {
                logger.warn("Invalid block header signature for block-rid: ${blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}: ${e.message}")
                return false
            }

            val newInfo = anchorOpData.toMinimalBlockHeaderInfo()

            val headers = chainHeadersMap.computeIfAbsent(bcRid) { mutableSetOf() }
            if (headers.all { header -> header.headerHeight != newInfo.headerHeight }) { // Rather primitive, but should be enough
                headers.add(newInfo)
            } else {
                logger.warn("Adding the same header twice, bc RID: ${bcRid.toHex()}, height ${newInfo.headerHeight}. New block: $newInfo")
                return false
            }
        }

        val relevantPipes = anchoringReceiver.getRelevantPipes()
        // Go through it chain by chain
        for ((bcRid, minimalHeaders) in chainHeadersMap) {
            // Each chain must be validated by itself b/c we must now look for gaps in the blocks etc.
            // and we pass that task to the [GenericBlockHeaderValidator]
            val validationResult = chainValidation(bctx, bcRid, minimalHeaders)
            if (validationResult.result != ValidationResult.Result.OK) {
                logger.warn(
                        "Failing to anchor a block for blockchain ${bcRid.toHex()}. ${validationResult.message}"
                )
                return false
            }
            // Clear matching pipe (if we have one)
            relevantPipes.find { it.blockchainRid == bcRid }?.let { pipe ->
                val lastHeight = minimalHeaders.maxOf { it.headerHeight }
                pipe.markTaken(lastHeight, bctx)
            }
        }
        return true
    }

    /**
     * Checks all headers we have for specific chain
     *
     * General check:
     *   Initially we compare the height we see in the header with what we expect from our local table
     *   However, we might anchor multiple headers from one chain at the same time, so there is some sorting to
     *   do if we intend to discover gaps.
     *
     * @param bcRid is the chain we intend to validate
     * @param minimalHeaders is a very small data set for each header (that we use for basic validation)
     * @return the result of the validation
     */
    private fun chainValidation(
            ctxt: EContext,
            bcRid: BlockchainRid,
            minimalHeaders: Set<MinimalBlockHeaderInfo>
    ): ValidationResult {
        // Restructure to the format the Validator needs
        val myHeaderMap = minimalHeaders.associateBy { it.headerHeight }

        // Run the validator
        return GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(
                bcRid,
                myHeaderMap,
                getExpectedData(ctxt, bcRid)
        ) { getAnchoredBlockAtHeight(ctxt, bcRid, it)?.blockRid?.data }
    }

    /**
     * @return the data we expect to find, fetched from anchoring module's own tables,
     *         or null if we've never anchored any block for this chain before.
     */
    private fun getExpectedData(ctxt: EContext, bcRid: BlockchainRid): MinimalBlockHeaderInfo? =
            getLastAnchoredBlock(ctxt, bcRid)?.let {
                // We found something, return it
                MinimalBlockHeaderInfo(
                        it.blockRid,
                        null,
                        it.height
                ) // Don't care about the prev block here
            }

    /**
     * Ask the anchoring Module for last anchored block
     *
     * @param bcRid is the chain we are interested in
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getLastAnchoredBlock(ctxt: EContext, bcRid: BlockchainRid): TempBlockInfo? {
        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
                Pair("blockchain_rid", gtv(bcRidByteArr))
        )
        val block = module.query(ctxt, "get_last_anchored_block", args)
        return if (block == GtvNull) {
            null
        } else {
            TempBlockInfo.fromBlock(block)
        }
    }

    /**
     * Ask the anchoring module for anchored block at height
     *
     * @param bcRid is the chain we are interested in
     * @param height is the block height we want to look at
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getAnchoredBlockAtHeight(ctxt: EContext, bcRid: BlockchainRid, height: Long): TempBlockInfo? {
        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
                Pair("blockchain_rid", gtv(bcRidByteArr)),
                Pair("height", gtv(height))
        )
        val block = module.query(ctxt, "get_anchored_block_at_height", args)
        return if (block == GtvNull) {
            null
        } else {
            TempBlockInfo.fromBlock(block)
        }
    }

    private fun buildArgs(vararg args: Pair<String, Gtv>): Gtv = gtv(*args)

    /**
     * Not really a domain object, just used to return some data
     */
    data class TempBlockInfo(
            val blockRid: BlockRid,
            val height: Long
    ) {
        companion object {
            fun fromBlock(block: Gtv): TempBlockInfo {
                return TempBlockInfo(
                        BlockRid(block["block_rid"]!!.asByteArray()),
                        block["block_height"]!!.asInteger()
                )
            }
        }
    }
}
