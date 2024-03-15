// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.Storage
import java.lang.Long.max
import java.util.concurrent.atomic.AtomicLong

class AnchoringLocalPipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        private val storage: Storage
) : AnchoringPipe {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    override fun numberOfNewPackets() = highestSeen.get() - lastCommitted.get()

    override fun fetchNextRange(fromHeight: Long, limit: Long): List<AnchoringPacket> {
        return withReadConnection(storage, chainID) { eContext ->
            val dba = DatabaseAccess.of(eContext)

            val packets = mutableListOf<AnchoringPacket>()

            while (packets.size < limit) {
                val height = fromHeight + packets.size
                val blockRID = dba.getBlockRID(eContext, height)
                if (blockRID != null) {
                    highestSeen.getAndUpdate { max(it, height) }
                    // Get raw data
                    val rawHeader = dba.getBlockHeader(eContext, blockRID)
                    val rawWitness = dba.getWitnessData(eContext, blockRID)

                    packets.add(AnchoringPacket(height, blockRID, rawHeader, rawWitness))
                } else {
                    break
                }
            }

            // Are there more blocks available?
            if (packets.size.toLong() == limit) {
                val height = fromHeight + packets.size + 1
                val blockRID = dba.getBlockRID(eContext, height)
                if (blockRID != null) {
                    highestSeen.getAndUpdate { max(it, height) }
                }
            }

            packets
        }
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
