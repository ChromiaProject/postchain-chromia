package net.postchain.hybridcompute

import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

@Suppress("unused")
class HybridComputeGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
) {
    private val dbOperations = HybridComputeDatabaseOperationsImpl()
    private val specialTransactionExtension = HybridComputeSpecialTransactionExtension(dbOperations)

    override fun initializeDB(ctx: EContext) {
        dbOperations.initialize(ctx)
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf(specialTransactionExtension)
}
