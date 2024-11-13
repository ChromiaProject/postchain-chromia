// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable

interface AnchoringPipe : Shutdownable {
    companion object {
        const val MAX_PACKETS_PER_REQUEST = 20L
    }

    val chainID: Long
    val blockchainRid: BlockchainRid
    fun newBlockAvailable(height: Long)
    fun mightHaveNewPackets(): Boolean
    fun numberOfNewPackets(): Long
    fun fetchNextRange(fromHeight: Long): List<AnchoringPacket>
    fun markTaken(lastCommittedHeight: Long, bctx: BlockEContext)
}