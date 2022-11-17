// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.Storage
import java.lang.Long.max
import java.util.concurrent.atomic.AtomicLong

class ClusterAnchoringLocalPipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        private val storage: Storage
) : ClusterAnchoringPipe {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    // TODO: prefetch packet in dispatcher instead of just setting height
    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    override fun fetchNext(currentPointer: Long): ClusterAnchoringPacket? {
        return withReadConnection(storage, chainID) { eContext ->
            val dba = DatabaseAccess.of(eContext)

            val blockRID = dba.getBlockRID(eContext, currentPointer)
            if (blockRID != null) {
                highestSeen.getAndUpdate { max(it, currentPointer) }
                // Get raw data
                val rawHeader = dba.getBlockHeader(eContext, blockRID)
                val rawWitness = dba.getWitnessData(eContext, blockRID)

                ClusterAnchoringPacket(currentPointer, blockRID, rawHeader, rawWitness)
            } else {
                null
            }
        }
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
