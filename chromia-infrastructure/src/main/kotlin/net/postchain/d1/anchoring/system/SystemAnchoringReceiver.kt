package net.postchain.d1.anchoring.system

import net.postchain.d1.anchoring.AnchoringPipe
import net.postchain.d1.anchoring.AnchoringReceiver
import net.postchain.d1.cluster.ClusterManagement

class SystemAnchoringReceiver(private val clusterManagement: ClusterManagement) : AnchoringReceiver {
    override val localPipes = mutableMapOf<Long, AnchoringPipe>()

    override fun getRelevantPipes(): List<AnchoringPipe> {
        val clusterAnchoringChains = clusterManagement.getClusterAnchoringChains()
        return localPipes.values.filter { it.blockchainRid in clusterAnchoringChains }
    }
}
