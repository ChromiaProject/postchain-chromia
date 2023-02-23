package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid

interface AnchoringReceiver {
    val localPipes: MutableMap<Long, AnchoringPipe>
    fun getRelevantPipes(): List<AnchoringPipe>

    fun getRelevantChains(): Set<BlockchainRid>
}