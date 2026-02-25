package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.PostchainContextAware

class StubHybridComputeEngine : HybridComputeEngine, PostchainContextAware {
    companion object : KLogging()

    override val name: String = "test"

    private var initialized = false
    private var loaded = false

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        initialized = true
    }

    override fun load() {
        loaded = true
    }

    override fun estimatePoints(input: Gtv): Long = 10L

    override fun compute(input: Gtv): Pair<Gtv, Long> {
        return gtv("output") to 10L
    }

    override fun validate(input: Gtv, output: Gtv) {}
}
