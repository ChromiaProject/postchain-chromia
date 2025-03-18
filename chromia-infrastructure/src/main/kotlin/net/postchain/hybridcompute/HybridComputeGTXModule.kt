package net.postchain.hybridcompute

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

@Suppress("unused")
class HybridComputeGTXModule() : GTXModule {
    private val specialTransactionExtension = HybridComputeSpecialTransactionExtension()

    override fun initializeDB(ctx: EContext) {}

    override fun getOperations(): Set<String> = setOf()

    override fun getQueries(): Set<String> = setOf()

    override fun makeTransactor(opData: ExtOpData): Transactor {
        throw UserMistake("Operation not found")
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        throw UserMistake("Query not found")
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = listOf()

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf(specialTransactionExtension)
}
