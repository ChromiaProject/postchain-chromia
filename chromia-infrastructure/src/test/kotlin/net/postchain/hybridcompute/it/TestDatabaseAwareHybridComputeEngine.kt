package net.postchain.hybridcompute.it

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.gtv.Gtv
import net.postchain.gtx.PostchainContextAware
import net.postchain.hybridcompute.DatabaseAwareHybridComputeEngine

@Suppress("unused")
class TestDatabaseAwareHybridComputeEngine : DatabaseAwareHybridComputeEngine, PostchainContextAware, Shutdownable {
    companion object : KLogging()

    override val name: String = "test3"

    private var initialized = false
    private var loaded = false

    private var loadFail = false
    private var loadTimeout = false

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        logger.info("init")
        if (configuration.rawConfig["hybridcompute"]!!.asDict()["load_fail"]?.asBoolean() == true) {
            loadFail = true
        }
        if (configuration.rawConfig["hybridcompute"]!!.asDict()["load_timeout"]?.asBoolean() == true) {
            loadTimeout = true
        }
        initialized = true
    }

    override fun load() {
        throw ProgrammerMistake("load() should not be invoked in DatabaseAwareHybridComputeEngine")
    }

    override fun load(ctx: EContext) {
        require(initialized) { "Not initialized" }
        logger.info("Load starting for chain ${ctx.chainID}")
        Thread.sleep(1000)
        if (loadFail) {
            throw UserMistake("Load failed")
        }
        if (loadTimeout) {
            Thread.sleep(10000)
        }
        logger.info("Load finished for chain ${ctx.chainID}")
        loaded = true
    }

    override fun estimatePoints(input: Gtv): Long = 10L

    override fun compute(input: Gtv): Pair<Gtv, Long> {
        throw ProgrammerMistake("compute(Gtv) should not be invoked in DatabaseAwareHybridComputeEngine")
    }

    override fun compute(ctx: EContext, input: Gtv): Pair<Gtv, Long> {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Compute starting for chain ${ctx.chainID}")
        val behavior = TestEngineBehavior.decode(input)
        val output = behavior.compute(input)
        logger.info("Compute finished for chain ${ctx.chainID}")
        return output to 10
    }

    override fun validate(input: Gtv, output: Gtv) {
        throw ProgrammerMistake("validate(Gtv, Gtv) should not be invoked in DatabaseAwareHybridComputeEngine")
    }

    override fun validate(bctx: BlockEContext, input: Gtv, output: Gtv) {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Validate starting for block ${bctx.height}")
        val behavior = TestEngineBehavior.decode(input)
        behavior.validate(input, output)
        logger.info("Validate finished for block ${bctx.height}")
    }

    override fun shutdown() {
        require(initialized) { "Not initialized" }
        logger.info("shutdown")
        initialized = false
    }
}
