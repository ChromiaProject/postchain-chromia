// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.client.core.PostchainBlockClient
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

    private val client: PostchainBlockClient = MasterClient(connectionManager.masterSubQueryManager, blockchainRid)

    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    override fun numberOfNewPackets() = highestSeen.get() - lastCommitted.get()

    override fun fetchNext(currentPointer: Long): AnchoringPacket? =
            try {
                client.blockAtHeight(currentPointer)
            } catch (e: Exception) {
                logger.warn(e) { "Block fetching from sub node failed: $e" }
                null
            }?.let {
                AnchoringPacket(
                        currentPointer,
                        it.rid.data,
                        it.header.data,
                        it.witness.data
                )
            }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
