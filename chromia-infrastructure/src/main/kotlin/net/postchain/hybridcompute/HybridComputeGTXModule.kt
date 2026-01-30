package net.postchain.hybridcompute

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

@Suppress("unused")
class HybridComputeGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
), PostchainContextAware {
    private val dbOperations = HybridComputeDatabaseOperationsImpl()
    private lateinit var postchainContext: PostchainContext
    private val specialTransactionExtension by lazy { HybridComputeSpecialTransactionExtension(dbOperations, postchainContext) }

    override fun initializeDB(ctx: EContext) {
        dbOperations.initialize(ctx)
    }

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        this.postchainContext = postchainContext
    }
    
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf(specialTransactionExtension)
}
