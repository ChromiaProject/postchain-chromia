package net.postchain.d1.anchoring.system

import net.postchain.common.BlockchainRid
import net.postchain.d1.anchoring.AnchoringPipe
import net.postchain.d1.anchoring.AnchoringReceiver
import net.postchain.d1.cluster.ClusterManagement
import java.util.concurrent.ConcurrentHashMap

class SystemAnchoringReceiver(private val clusterManagement: ClusterManagement) : AnchoringReceiver {

    override val cluster = "system"
    override val localPipes = ConcurrentHashMap<Long, AnchoringPipe>()

    override fun getRelevantPipes(): List<AnchoringPipe> {
        return localPipes.values.filter { it.blockchainRid in getRelevantChains() }
    }

    override fun getRelevantChains(includeRemovedChainsSince: Long?): Set<BlockchainRid> {
        return clusterManagement.getClusterAnchoringChains().toSet()
    }
}
