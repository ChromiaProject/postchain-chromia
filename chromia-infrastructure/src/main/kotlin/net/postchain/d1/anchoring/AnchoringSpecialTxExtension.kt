package net.postchain.d1.anchoring

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
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
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.d1.Validation
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.getCachedPeers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXBlockBuildingAffectingSpecialTxExtension
import java.time.Clock
import java.time.Duration

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
open class AnchoringSpecialTxExtension(private val clock: Clock = Clock.systemUTC(),
                                       private val anchoringReceiverFactory: AnchoringReceiverFactory)
    : GTXBlockBuildingAffectingSpecialTxExtension {

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
        const val OP_BATCH_BLOCK_HEADER = "__batch_anchor_block_header"

        val REMOVED_BLOCKCHAIN_GRACE_PERIOD: Duration = Duration.ofHours(1L)
    }

    private val _relevantOps = setOf(OP_BLOCK_HEADER, OP_BATCH_BLOCK_HEADER)
    private val peerCache = mutableMapOf<BlockchainRid, Pair<ByteArray, Collection<PubKey>>>()

    lateinit var isSigner: () -> Boolean
    lateinit var anchoringReceiver: AnchoringReceiver
    lateinit var clusterManagement: ClusterManagement
    lateinit var blockchainConfigProvider: BlockchainConfigProvider
    lateinit var anchoringConfig: AnchoringBlockchainConfigData
    var maxTxSize: Long = -1

    private var firstAnchorBlockTime = 0L

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

        // Extract all packages from all pipes
        val specialTxBuilder = if (anchoringConfig.batchMode) BatchAnchoringSpecialTxBuilder() else MultiOpAnchoringSpecialTxBuilder()
        var currentSize = specialTxBuilder.getTxOverheadSize()
        pipeIt@ for (pipe in pipes) {
            var opsCount = 0
            var currentHeight: Long = getLastAnchoredHeight(bctx, pipe.blockchainRid)
            pipePacketsIt@ while (pipe.mightHaveNewPackets()) {
                val anchorPackets = pipe.fetchNextRange(currentHeight + 1)
                if (anchorPackets.isEmpty()) {
                    break // Nothing more to find
                } else {
                    for (anchorPacket in anchorPackets) {
                        val size = specialTxBuilder.calculateRequiredSize(anchorPacket)
                        if (currentSize + size > maxTxSize - TX_SIZE_MARGIN) {
                            break@pipeIt
                        }
                        specialTxBuilder.addAnchorPacket(anchorPacket)
                        opsCount++
                        currentHeight++
                        currentSize += size

                        if (anchoringConfig.maxBlocksPerChain > 0 && opsCount + 1 > anchoringConfig.maxBlocksPerChain) {
                            break@pipePacketsIt
                        }
                    }
                }
            }
        }
        return specialTxBuilder.build()
    }

    open fun numberOfBlocksToAnchor(): Long = if (!::anchoringReceiver.isInitialized) 0 else
        anchoringReceiver.getRelevantPipes().sumOf { if (it.numberOfNewPackets() > 0) it.numberOfNewPackets() else 0 }

    private fun getLastAnchoredHeight(ctxt: EContext, blockchainRID: BlockchainRid): Long =
            getLastAnchoredBlock(ctxt, blockchainRID)?.height ?: -1

    /**
     * We look at the content of all operations (to check if the block headers are ok and nothing is missing)
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        val chainHeadersMap = mutableMapOf<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>()
        val relevantChains = anchoringReceiver.getRelevantChains(bctx.timestamp - REMOVED_BLOCKCHAIN_GRACE_PERIOD.toMillis())
        pruneCachedPeers(relevantChains)

        val validatedAnchoringOps = if (anchoringConfig.batchMode) {
            if (ops.size != 1) {
                logger.warn("Only one operation allowed when batching")
                return false
            }

            AnchoringOpData.validateAndDecodeBatchOpData(ops[0]) ?: return false
        } else {
            val validatedOps = mutableListOf<AnchoringOpData>()
            for (op in ops) {
                val anchorOpData = AnchoringOpData.validateAndDecodeOpData(op) ?: return false
                validatedOps.add(anchorOpData)
            }
            validatedOps
        }

        val signatureVerificationJobs = mutableListOf<Pair<Collection<PubKey>, AnchoringOpData>>()
        for (anchorOpData in validatedAnchoringOps) {
            val headerData = anchorOpData.headerData
            val bcRid = BlockchainRid(headerData.getBlockchainRid())
            if (isSigner() && bcRid !in relevantChains) {
                logger.warn("Blocks from blockchain $bcRid are not allowed to be anchored in this chain")
                return false
            }

            val blockRid = headerData.toGtv().merkleHash(GtvMerkleHashCalculatorV1(cryptoSystem))
            if (!blockRid.contentEquals(anchorOpData.blockRid)) {
                logger.warn("Invalid block-rid: ${anchorOpData.blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}, expected: ${blockRid.toHex()}")
                return false
            }

            val peers = getCachedPeers(peerCache, headerData, blockchainConfigProvider) ?: return false
            signatureVerificationJobs.add(peers to anchorOpData)

            val newInfo = anchorOpData.toMinimalBlockHeaderInfo()

            val headers = chainHeadersMap.computeIfAbsent(bcRid) { mutableSetOf() }
            if (headers.all { header -> header.headerHeight != newInfo.headerHeight }) { // Rather primitive, but should be enough
                headers.add(newInfo)
            } else {
                logger.warn("Adding the same header twice, bc RID: ${bcRid.toHex()}, height ${newInfo.headerHeight}. New block: $newInfo")
                return false
            }
        }

        val allSignaturesValid = runBlocking {
            coroutineScope {
                val context = Dispatchers.Default + CoroutineName("anchoring-signature-validation") + MDCContext()
                signatureVerificationJobs.map { (peers, anchorOpData) ->
                    async(context) {
                        val headerData = anchorOpData.headerData
                        val blockRid = anchorOpData.blockRid
                        try {
                            val witness = BaseBlockWitness.fromBytes(anchorOpData.witness)
                            Validation.validateBlockSignatures(cryptoSystem, headerData.getPreviousBlockRid(), GtvEncoder.encodeGtv(headerData.toGtv()), blockRid, peers, witness)
                            true
                        } catch (e: UserMistake) {
                            logger.warn("Invalid block header signature for block-rid: ${blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}: ${e.message}")
                            false
                        }
                    }
                }.all { it.await() }
            }
        }
        if (!allSignaturesValid) return false

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

    private fun pruneCachedPeers(relevantChains: Set<BlockchainRid>) {
        (peerCache.keys - relevantChains).forEach {
            peerCache.remove(it)
        }
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

    override fun blockCommitted(blockData: BlockData) {
        firstAnchorBlockTime = 0
    }

    override fun shouldBuildBlock(): Boolean {
        if (!::anchoringConfig.isInitialized) return false

        val now = clock.millis()
        if (firstAnchorBlockTime > 0 && now - firstAnchorBlockTime > anchoringConfig.maxAnchoringDelay) return true

        val numberOfBlocksToAnchor: Long = try {
            numberOfBlocksToAnchor()
        } catch (e: Exception) {
            logger.error("Could not fetch number of blocks to anchor", e)
            return false
        }
        if (numberOfBlocksToAnchor >= anchoringConfig.maxAnchoringBlocksPerAnchorBlock) return true
        if (firstAnchorBlockTime == 0L && numberOfBlocksToAnchor > 0) {
            firstAnchorBlockTime = now
            return false
        }

        return false
    }

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
