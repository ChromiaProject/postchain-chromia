// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.d1.query.MasterClient
import net.postchain.network.mastersub.master.MasterConnectionManager
import java.lang.Long.max
import java.util.concurrent.atomic.AtomicLong

class AnchoringSubnodePipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        connectionManager: MasterConnectionManager
) : AnchoringPipe {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    companion object : KLogging()

    private val client: MasterClient = MasterClient(connectionManager.masterSubQueryManager, blockchainRid)

    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    override fun numberOfNewPackets() = highestSeen.get() - lastCommitted.get()

    override fun fetchNextRange(fromHeight: Long, limit: Long): List<AnchoringPacket> = try {
        client.blocksFromHeight(fromHeight, limit).map {
            AnchoringPacket(
                    fromHeight,
                    it.rid.data,
                    it.header.data,
                    it.witness.data
            )
        }
    } catch (e: Exception) {
        logger.warn(e) { "Fetching the block range starting from height $fromHeight for $blockchainRid from the subnode failed: $e" }
        listOf()
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
