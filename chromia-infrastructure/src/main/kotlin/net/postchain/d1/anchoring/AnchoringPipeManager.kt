package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import java.util.concurrent.ConcurrentHashMap

class AnchoringPipeManager(
        private val anchoringPipeFactory: AnchoringPipeFactory,
        val relevantChainsProvider: RelevantChainsProvider
) : Shutdownable {

    private val anchoringPipes = ConcurrentHashMap<BlockchainRid, AnchoringPipe>()

    init {
        getRelevantPipes() // Initialize pipes
    }

    fun getRelevantPipes(): List<AnchoringPipe> = relevantChainsProvider.getRelevantChains().mapNotNull { blockchainRid ->
        anchoringPipes[blockchainRid] ?: anchoringPipeFactory.create(blockchainRid)?.also {
            anchoringPipes[blockchainRid] = it
        }
    }

    fun afterCommit(blockchainRid: BlockchainRid, height: Long) {
        anchoringPipes[blockchainRid]?.newBlockAvailable(height)
    }

    fun onChainConnect(blockchainRid: BlockchainRid) {
        if (blockchainRid in relevantChainsProvider.getRelevantChains()) anchoringPipeFactory.create(blockchainRid)?.also {
            anchoringPipes[blockchainRid] = it
        }
    }

    fun onChainDisconnect(blockchainRid: BlockchainRid) {
        anchoringPipes.remove(blockchainRid)?.shutdown()
    }

    override fun shutdown() {
        anchoringPipes.values.forEach { it.shutdown() }
    }
}
