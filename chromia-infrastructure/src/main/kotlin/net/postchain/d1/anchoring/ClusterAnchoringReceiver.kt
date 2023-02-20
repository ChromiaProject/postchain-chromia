// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.d1.cluster.ClusterManagement

class ClusterAnchoringReceiver(private val cluster: String, private val clusterManagement: ClusterManagement) {
    val localPipes = mutableMapOf<Long, ClusterAnchoringPipe>()

    fun getRelevantPipes(): List<ClusterAnchoringPipe> {
        val activeClusterChains = clusterManagement.getActiveBlockchains(cluster)
        return localPipes.values.filter { it.blockchainRid in activeClusterChains }
    }
}