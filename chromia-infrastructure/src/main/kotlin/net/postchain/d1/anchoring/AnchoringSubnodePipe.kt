// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockQueries
import net.postchain.d1.anchoring.AnchoringPipe.Companion.MAX_PACKETS_PER_REQUEST
import net.postchain.d1.query.MasterClient
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.network.mastersub.master.MasterConnectionManager
import java.lang.Long.max
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class AnchoringSubnodePipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        connectionManager: MasterConnectionManager,
        private val anchorBlockQueries: BlockQueries,
        private val blockFetchLimit: Int = DEFAULT_BLOCK_FETCH_LIMIT,
) : AnchoringPipe {
    private val highestFetched = AtomicLong(-1L)
    private val packets = ConcurrentSkipListMap<Long, AnchoringPacket>() // block height to packet

    companion object : KLogging() {
        val pollInterval = 60.seconds
        const val DEFAULT_BLOCK_FETCH_LIMIT = 200
    }

    private val client: MasterClient = MasterClient(connectionManager.masterSubQueryManager, blockchainRid)

    private val executor = Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder().setNameFormat(
                    "subnode-anchoring-pipe-worker-chainID-$chainID-blockchainRid-${blockchainRid.toHex()}"
            ).build()
    )

    @Volatile
    private var currentTask: ScheduledFuture<*> =
            executor.schedule(::tryFetchBlocksAndReschedule, pollInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    override fun newBlockAvailable(height: Long) {
        if (height > highestFetched.get()) { // Trigger a fetch since we don't already have this block
            currentTask.cancel(false)
            currentTask = executor.schedule(::tryFetchBlocksAndReschedule, 0L, TimeUnit.MILLISECONDS)
        }
    }

    override fun mightHaveNewPackets() = packets.isNotEmpty()

    override fun numberOfNewPackets() = packets.size.toLong()

    private fun tryFetchBlocksAndReschedule() {
        withLoggingContext(mapOf(
                CHAIN_IID_TAG to chainID.toString(),
                BLOCKCHAIN_RID_TAG to blockchainRid.toHex()
        )) {
            try {
                if (packets.size < blockFetchLimit) {
                    logger.debug { "Fetching blocks" }
                    fetchBlocks()
                    logger.debug { "Fetched blocks" }
                } else {
                    logger.debug { "Reached the limit of $blockFetchLimit unprocessed fetched blocks, skipping fetch" }
                }
            } catch (e: UserMistake) {
                logger.warn(e.message)
            } catch (e: Exception) {
                logger.error("Block fetch failed: ${e.message}", e)
            }
        }
        currentTask.cancel(false)
        currentTask = executor.schedule(::tryFetchBlocksAndReschedule, pollInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private fun fetchBlocks() {
        val highestFetchHeight = highestFetched.get()
        val lastHeight = if (highestFetchHeight < 0) {
            fetchLastAnchoredHeight()
        } else {
            highestFetchHeight
        }
        fetchBlocksFromHeight(lastHeight + 1)
    }

    private fun fetchLastAnchoredHeight(): Long = anchorBlockQueries
            .query("get_last_anchored_block_height", gtv(mapOf("blockchain_rid" to gtv(blockchainRid))))
            .toCompletableFuture().get()
            .let { (it as? GtvInteger)?.integer ?: -1 }

    private fun fetchBlocksFromHeight(fromHeight: Long) {
        val fetchedPackets = try {
            client.blocksFromHeight(fromHeight, MAX_PACKETS_PER_REQUEST).map {
                AnchoringPacket(
                        height = it.blockHeight,
                        blockRid = it.blockRid,
                        rawHeader = it.blockHeader,
                        rawWitness = it.witness,
                )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Fetching the block range starting from height $fromHeight for $blockchainRid from the subnode failed: $e" }
            listOf()
        }

        var maxHeight = -1L
        for (packet in fetchedPackets) {
            maxHeight = max(maxHeight, packet.height)
            packets[packet.height] = packet
        }
        highestFetched.getAndUpdate { max(it, maxHeight) }
    }

    override fun fetchNextRange(fromHeight: Long): List<AnchoringPacket> =
            ArrayList(packets.tailMap(fromHeight, true).values)

    override fun markTaken(lastCommittedHeight: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            for (height in packets.headMap(lastCommittedHeight, true).keys) {
                packets.remove(height)
            }
        }
    }

    override fun shutdown() {
        executor.shutdownNow()
    }
}
