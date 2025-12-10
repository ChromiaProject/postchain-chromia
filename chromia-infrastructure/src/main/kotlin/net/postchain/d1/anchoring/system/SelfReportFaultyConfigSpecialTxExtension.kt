package net.postchain.d1.anchoring.system

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.FAILED_CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

const val SELF_REPORT_FAULTY_CONFIG_OP = "__self_report_faulty_config"

class SelfReportFaultyConfigSpecialTxExtension : GTXSpecialTxExtension {

    companion object : KLogging()

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val failedConfigHash = getFailedConfigHashFromPreviousBlock(DatabaseAccess.of(bctx), bctx)
        return if (failedConfigHash != null) {
            listOf(OpData(SELF_REPORT_FAULTY_CONFIG_OP, arrayOf(gtv(failedConfigHash))))
        } else listOf()
    }

    override fun getRelevantOps() = setOf(SELF_REPORT_FAULTY_CONFIG_OP)

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {}

    override fun needsSpecialTransaction(position: SpecialTransactionPosition) = position == SpecialTransactionPosition.Begin

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (ops.size != 1) {
            throw UserMistake("Invalid amount of $SELF_REPORT_FAULTY_CONFIG_OP operations received: ${ops.size}, only expecting 1")
        }

        val faultyConfigOp = ops.first()
        if (faultyConfigOp.args.size != 1 || faultyConfigOp.args[0] !is GtvByteArray) {
            throw UserMistake("Received $SELF_REPORT_FAULTY_CONFIG_OP operations with invalid args ${faultyConfigOp.args}, expecting one argument of byte array type")
        }

        val receivedFaultyConfigHash = faultyConfigOp.args[0].asByteArray()
        val faultyConfigHash = getFailedConfigHashFromPreviousBlock(DatabaseAccess.of(bctx), bctx)
        if (faultyConfigHash == null) {
            throw UserMistake("Received $SELF_REPORT_FAULTY_CONFIG_OP but previous block does not contain any faulty config hash extra header")
        } else if (!faultyConfigHash.contentEquals(receivedFaultyConfigHash)) {
            throw UserMistake("Received $SELF_REPORT_FAULTY_CONFIG_OP with faulty config hash ${receivedFaultyConfigHash.toHex()} but expected ${faultyConfigHash.toHex()}")
        }
        return true
    }

    private fun getFailedConfigHashFromPreviousBlock(dba: DatabaseAccess, bctx: BlockEContext): ByteArray? {
        val previousBlockRid = dba.getBlockRID(bctx, bctx.height - 1) ?: return null // Nothing to do on initial block
        val previousBlockHeader = BlockHeaderData.fromBinary(dba.getBlockHeader(bctx, previousBlockRid))
        return previousBlockHeader.getExtra()[FAILED_CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
    }
}
