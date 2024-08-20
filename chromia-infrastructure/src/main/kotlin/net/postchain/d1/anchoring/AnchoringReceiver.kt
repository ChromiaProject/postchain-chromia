package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import java.util.concurrent.ConcurrentMap

interface AnchoringReceiver {
    val localPipes: ConcurrentMap<Long, AnchoringPipe>
    fun getRelevantPipes(): List<AnchoringPipe>

    fun getRelevantChains(includeRemovedChainsSince: Long? = null): Set<BlockchainRid>
}