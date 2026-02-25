package net.postchain.hybridcompute.it

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.gtv.Gtv
import net.postchain.gtx.PostchainContextAware
import net.postchain.hybridcompute.HybridComputeEngine

@Suppress("unused")
class TestHybridComputeEngine : HybridComputeEngine, PostchainContextAware, Shutdownable {
    companion object : KLogging()

    override val name: String = "test"

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
        require(initialized) { "Not initialized" }
        logger.info("Load starting")
        Thread.sleep(1000)
        if (loadFail) {
            throw UserMistake("Load failed")
        }
        if (loadTimeout) {
            Thread.sleep(10000)
        }
        logger.info("Load finished")
        loaded = true
    }

    override fun estimatePoints(input: Gtv): Long = 10L

    override fun compute(input: Gtv): Pair<Gtv, Long> {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Compute starting")
        val behavior = TestEngineBehavior.decode(input)
        val output = behavior.compute(input)
        logger.info("Compute finished")
        return output to 10
    }

    override fun validate(input: Gtv, output: Gtv) {
        require(initialized) { "Not initialized" }
        require(loaded) { "Not loaded" }
        logger.info("Validate starting")
        val behavior = TestEngineBehavior.decode(input)
        behavior.validate(input, output)
        logger.info("Validate finished")
    }

    override fun shutdown() {
        require(initialized) { "Not initialized" }
        logger.info("shutdown")
        initialized = false
    }
}
