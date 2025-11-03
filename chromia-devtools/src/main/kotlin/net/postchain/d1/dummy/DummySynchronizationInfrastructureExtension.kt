package net.postchain.d1.dummy

import net.postchain.PostchainContext
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.gtx.GTXModuleAware

class DummySynchronizationInfrastructureExtension(postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    val tickerServices = mutableMapOf<Long, TickerService>()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()

        if (cfg is GTXModuleAware) {
            cfg.module.getSpecialTxExtensions().filterIsInstance<DummySpecialTxExtension>().firstOrNull()?.let { txExt ->
                val newTickerService = TickerService().also { it.start() }
                txExt.tickerService = newTickerService
                tickerServices[cfg.chainID] = newTickerService
            }
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        tickerServices.remove(process.blockchainEngine.chainID)?.stop()
    }

    override fun shutdown() {
        tickerServices.forEach { it.value.stop() }
        tickerServices.clear()
    }
}
