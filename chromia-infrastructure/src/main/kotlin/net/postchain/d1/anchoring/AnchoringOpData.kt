package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.base.data.MinimalBlockHeaderInfo
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockRid
import net.postchain.gtv.Gtv
import net.postchain.gtx.data.OpData
import javax.swing.Spring.height

/**
 * This data class hides the ordering of the [OpData] arguments to the outside world
 * Can do primitive validation of [OpData] too.
 */
data class AnchoringOpData(
        val blockRid: ByteArray,
        val headerData: BlockHeaderData,
        val witness: ByteArray
) {

    companion object : KLogging() {

        /**
         * Does first part of validation, and hands over a "decoded" [AnchoringOpData] for further validation
         *
         * @param op is what we should validate/decode
         * @return is a simple DTO or null if decode failed
         */
        fun validateAndDecodeOpData(op: OpData): AnchoringOpData? {
            if (AnchoringSpecialTxExtension.OP_BLOCK_HEADER != op.opName) {
                logger.info("Invalid spcl operation: Expected op name ${AnchoringSpecialTxExtension.OP_BLOCK_HEADER} got ${op.opName}.")
                return null
            }

            if (op.args.size != 3) {
                logger.info("Invalid spcl operation: Expected 3 arg but got ${op.args.size}.")
                return null
            }

            return try {
                decodeFromGtvArray(op.args)
            } catch (e: RuntimeException) {
                logger.warn("Invalid spcl operation: Error: $e")
                null // We don't really care what's wrong, just log it and return null
            }
        }

        fun validateAndDecodeBatchOpData(op: OpData): List<AnchoringOpData>? {
            if (AnchoringSpecialTxExtension.OP_BATCH_BLOCK_HEADER != op.opName) {
                logger.info("Invalid spcl operation: Expected op name ${AnchoringSpecialTxExtension.OP_BATCH_BLOCK_HEADER} got ${op.opName}.")
                return null
            }

            if (op.args.size != 1) {
                logger.info("Invalid spcl operation: Expected 1 arg but got ${op.args.size}.")
                return null
            }

            return try {
                val blocksToAnchor = op.args[0].asArray()
                blocksToAnchor.map { decodeFromGtvArray(it.asArray()) }
            } catch (e: RuntimeException) {
                logger.warn("Invalid spcl operation: Error: $e")
                null // We don't really care what's wrong, just log it and return null
            }
        }

        private fun decodeFromGtvArray(array: Array<out Gtv>): AnchoringOpData {
            val blockRid = array[0].asByteArray()
            val header = BlockHeaderData.fromGtv(array[1])
            val rawWitness = array[2].asByteArray()

            if (header.getHeight() < 0) { // Ok, pretty stupid check, but why not
                throw ProgrammerMistake(
                        "Someone is trying to anchor a block for blockchain: " +
                                "${BlockchainRid(header.getBlockchainRid()).toHex()} at height = ${header.getHeight()} (which is impossible!). "
                )
            }

            return AnchoringOpData(blockRid, header, rawWitness)
        }
    }

    fun toMinimalBlockHeaderInfo(): MinimalBlockHeaderInfo {
        val headerBlockRid = BlockRid(blockRid)
        val headerPrevBlockRid = BlockRid(headerData.getPreviousBlockRid())
        val newBlockHeight = headerData.getHeight()
        return MinimalBlockHeaderInfo(headerBlockRid, headerPrevBlockRid, newBlockHeight)
    }
}

fun MinimalBlockHeaderInfo.toStr(): String {
    return "MinimalBlockHeaderInfo(blockRid:${this.headerBlockRid.toHex()}, headerPrevBlockRid:${this.headerPrevBlockRid?.toHex()}, headerHeight:${headerHeight})"
}
