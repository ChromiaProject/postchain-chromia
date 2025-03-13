package net.postchain.hybridcompute

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

data class HybridComputeConfig(
        val engine: String,

        @Name("compute_timeout_seconds")
        val computeTimeoutSeconds: Long,

        @DefaultValue(defaultLong = 1)
        val concurrency: Long,
)

@Suppress("unused")
class HybridComputeGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule {
        val moduleConfig = config.asDict()["hybridcompute"]?.toObject<HybridComputeConfig>()
                ?: throw UserMistake("hybridcompute configuration not found")
        require(moduleConfig.computeTimeoutSeconds > 0) { "compute_timeout_seconds must be greater than 0" }
        require(moduleConfig.concurrency > 0) { "concurrency must be greater than 0" }
        val engine = newInstanceOf<HybridComputeEngine>(moduleConfig.engine)
        engine.init(config, blockchainRID)
        return HybridComputeGTXModule(engine, moduleConfig.computeTimeoutSeconds, moduleConfig.concurrency.toInt())
    }
}

internal class HybridComputeGTXModule(engine: HybridComputeEngine, computeTimeoutSeconds: Long, concurrency: Int) : GTXModule {
    private val specialTransactionExtension = HybridComputeSpecialTransactionExtension(
            engine,
            computeTimeoutSeconds,
            concurrency
    )

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

    override fun shutdown() {
        specialTransactionExtension.shutdown()
    }
}
