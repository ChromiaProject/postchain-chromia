package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class StubHybridComputeEngine : HybridComputeEngine {
    companion object : KLogging()

    override val name: String = "test"

    private var initialized = false
    private var loaded = false

    override fun init(blockchainConfig: Gtv, blockchainRID: BlockchainRid) {
        initialized = true
    }

    override fun load() {
        loaded = true
    }

    override fun compute(input: Gtv): Gtv {
        return gtv("test")
    }

    override fun validate(output: Gtv) {

    }

    override fun shutdown() {

    }
}

