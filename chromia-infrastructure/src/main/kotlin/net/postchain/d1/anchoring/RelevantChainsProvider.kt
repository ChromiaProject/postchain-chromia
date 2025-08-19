package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid

interface RelevantChainsProvider {
    fun getRelevantChains(includeRemovedChainsSince: Long? = null): Set<BlockchainRid>
}
