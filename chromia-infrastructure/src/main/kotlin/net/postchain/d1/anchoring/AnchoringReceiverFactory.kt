package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import net.postchain.d1.cluster.ClusterManagement

fun interface AnchoringReceiverFactory {
    fun create(clusterManagement: ClusterManagement, anchoringBlockchainRid: BlockchainRid): AnchoringReceiver
}
