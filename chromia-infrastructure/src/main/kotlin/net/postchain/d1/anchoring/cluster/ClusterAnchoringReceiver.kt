// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring.cluster

import net.postchain.common.BlockchainRid
import net.postchain.d1.anchoring.AnchoringPipe
import net.postchain.d1.anchoring.AnchoringReceiver
import net.postchain.d1.cluster.ClusterManagement
import java.util.concurrent.ConcurrentHashMap

class ClusterAnchoringReceiver(
        private val cluster: String,
        private val systemAnchoringChain: BlockchainRid?,
        private val clusterManagement: ClusterManagement
) : AnchoringReceiver {
    override val localPipes = ConcurrentHashMap<Long, AnchoringPipe>()

    override fun getRelevantPipes(): List<AnchoringPipe> {
        return localPipes.values.filter { it.blockchainRid in getRelevantChains() }
    }

    override fun getRelevantChains(includeRemovedChainsSince: Long?): Set<BlockchainRid> {
        val activeClusterChains = (if (includeRemovedChainsSince != null) {
            clusterManagement.getActiveBlockchains(cluster) + clusterManagement.getRemovedClusterBlockchains(cluster, includeRemovedChainsSince)
        } else clusterManagement.getActiveBlockchains(cluster)).toSet()
        return if (systemAnchoringChain != null) {
            activeClusterChains - systemAnchoringChain
        } else {
            activeClusterChains
        }
    }
}
