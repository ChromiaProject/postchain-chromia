// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

class ClusterAnchoringReceiver {
    val localPipes = mutableMapOf<Long, ClusterAnchoringPipe>()

    fun getRelevantPipes(): List<ClusterAnchoringPipe> {
        return localPipes.values.toList()
    }
}