// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext

interface ClusterAnchoringPipe {
    val chainID: Long
    val blockchainRid: BlockchainRid
    fun setHighestSeenHeight(height: Long)
    fun mightHaveNewPackets(): Boolean
    fun fetchNext(currentPointer: Long): ClusterAnchoringPacket?
    fun markTaken(currentPointer: Long, bctx: BlockEContext)
}