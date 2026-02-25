package net.postchain.d1.dummy

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

const val TICK_QUERY_NAME = "get_latest_tick"

class DummySpecialTxExtension : GTXSpecialTxExtension {
    private lateinit var module: GTXModule

    lateinit var tickerService: TickerService

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        this.module = module
    }

    override fun getRelevantOps() = setOf(TickOp.OP_NAME)

    override fun needsSpecialTransaction(position: SpecialTransactionPosition) = position == SpecialTransactionPosition.Begin

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val currentTick = module.query(bctx, TICK_QUERY_NAME, GtvDictionary.build(mapOf())).asInteger()
        tickerService.pruneUpTo(currentTick)

        val ops = mutableListOf<OpData>()
        if (::tickerService.isInitialized) {
            for (tick in tickerService.queue) {
                ops.add(TickOp(tick).toOpData())
            }
        }

        return ops
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        var currentTick = module.query(bctx, TICK_QUERY_NAME, GtvDictionary.build(mapOf())).asInteger()
        for (op in ops) {
            val tick = TickOp.fromOpData(op) ?: throw UserMistake("Invalid operation")
            if (tick.tick <= currentTick) {
                throw UserMistake("${tick.tick} <= $currentTick")
            }
            currentTick = tick.tick
        }

        bctx.addAfterCommitHook {
            tickerService.pruneUpTo(currentTick)
        }

        return true
    }
}

data class TickOp(
        val tick: Long,
) {
    companion object {
        // operation __tick(tick: integer)
        const val OP_NAME = "__tick"

        fun fromOpData(opData: OpData): TickOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 1) {
                return null
            }

            return try {
                TickOp(opData.args[0].asInteger())
            } catch (_: UserMistake) {
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(tick)))
}
