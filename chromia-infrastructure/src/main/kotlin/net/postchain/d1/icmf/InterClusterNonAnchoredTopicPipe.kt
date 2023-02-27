package net.postchain.d1.icmf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.rell.icmf.icmfGetMessagesAfterHeight
import net.postchain.gtv.GtvEncoder
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class InterClusterNonAnchoredTopicPipe(override val route: TopicRoute,
                                       override val id: BlockchainRid,
                                       private val clusterName: String,
                                       private val clientProvider: ChromiaClientProvider,
                                       lastMessageHeight: Long) : IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 10.seconds
        const val maxQueueSizeBytes = 32 * 1024 * 1024 // 32 MiB
    }

    private val blockchainRid = id
    private val packets = ConcurrentSkipListMap<Long, Pair<IcmfPackets<Long, IcmfPacket>, Int>>()
    private val currentQueueSizeBytes = AtomicInteger(0)
    private val lastMessageHeight = AtomicLong(lastMessageHeight)
    private val job: Job

    internal val queueIsEmpty: Boolean
        get() = currentQueueSizeBytes.get() == 0

    init {
        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("non-anchored-pipe-worker-cluster-$clusterName-topic-${route.topic}-chain-${blockchainRid.toHex()}")) {
            while (isActive) {
                try {
                    logger.info("Fetching messages")
                    fetchMessages()
                    logger.info("Fetched messages")
                } catch (e: CancellationException) {
                    break
                } catch (e: UserMistake) {
                    logger.warn(e.message)
                } catch (e: Exception) {
                    logger.error("Message fetch failed: ${e.message}", e)
                }
                delay(pollInterval)
            }
        }
    }

    private fun fetchMessages() {
        val clusterClient = clientProvider.cluster(clusterName)
        val query = clusterClient.blockchain(blockchainRid)

        val allMessages = query.icmfGetMessagesAfterHeight(
                route.topic,
                lastMessageHeight.get()
        ).map {
            val size = GtvEncoder.encodeGtv(it.body).size
            if (size > MAX_MESSAGE_SIZE) throw UserMistake("Message with size $size bytes exceeds maximum size: $MAX_MESSAGE_SIZE bytes")
            it.height to IcmfMessage(it.body, size)
        }.groupBy { it.first }.mapValues { messages -> messages.value.map { it.second } }

        val icmfPackets = mutableListOf<IcmfPacket>()
        for ((height, messages) in allMessages) {
            val block = query.blockAtHeight(height)
            if (block == null) {
                logger.warn("Unable to fetch block at height: $height from blockchain-rid: ${blockchainRid.toHex()}")
                return
            }

            val decodedHeader = BlockHeaderData.fromBinary(block.header.data)
            val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
            if (icmfHeaderData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: ${block.rid.toHex()} for blockchain-rid: ${blockchainRid.toHex()} at height: $height")
                return
            }

            val topicData = icmfHeaderData[route.topic]?.let { TopicHeaderData.fromGtv(it) }
            if (topicData == null) {
                logger.warn(
                        "$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${block.rid.toHex()} for blockchain-rid: ${
                            blockchainRid.toHex()
                        } at height: $height"
                )
                return
            }

            icmfPackets.add(
                    IcmfPacket(
                            height = height,
                            sender = blockchainRid,
                            topic = route.topic,
                            blockRid = block.rid.data,
                            rawHeader = block.header.data,
                            rawWitness = block.witness.data,
                            prevMessageBlockHeight = topicData.previousBlockHeight,
                            messages = messages
                    )
            )
        }

        // No new packets we can return
        if (icmfPackets.isEmpty()) return

        val packetsSizeBytes = icmfPackets.sumOf { packet ->
            packet.messages.sumOf { it.size }
        }
        val lastSeenHeight = icmfPackets.maxOf { it.height }
        if (currentQueueSizeBytes.get() + packetsSizeBytes <= maxQueueSizeBytes) {
            packets[lastSeenHeight] = IcmfPackets(lastSeenHeight, icmfPackets) to packetsSizeBytes
            currentQueueSizeBytes.addAndGet(packetsSizeBytes)
            lastMessageHeight.set(lastSeenHeight)
        } else {
            logger.info("pipe reached max capacity $maxQueueSizeBytes bytes")
        }
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long, IcmfPacket>? =
            packets.higherEntry(currentPointer)?.value?.first

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            for (height in packets.navigableKeySet().headSet(currentPointer, true)) {
                packets.remove(height)?.let {
                    currentQueueSizeBytes.addAndGet(-it.second)
                }
            }
        }
    }

    override fun shutdown() {
        job.cancel()
    }
}
