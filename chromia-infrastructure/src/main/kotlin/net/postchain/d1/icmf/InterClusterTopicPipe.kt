package net.postchain.d1.icmf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import mu.KLogging
import net.postchain.base.extension.getMerkleHashVersion
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

class InterClusterTopicPipe(
        override val route: TopicRoute,
        override val id: BlockchainRid,
        private val clientProvider: ChromiaClientProvider,
        private val skipToHeight: Long = 0,
        initialLastMessageHeight: Long
) : IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>, QueuedPipe {
    companion object : KLogging() {
        val pollInterval = 10.seconds
        const val maxQueueSizeBytes = 32 * 1024 * 1024 // 32 MiB
    }

    private val blockchainRid = id
    private val packets = ConcurrentSkipListMap<Long, Pair<IcmfPackets<Long, IcmfPacket>, Int>>()
    private val currentQueueSizeBytes = AtomicInteger(0)
    private val lastMessageHeight = AtomicLong(-1L)
    private val job: Job

    override val queueLength: Int
        get() = packets.size

    override val queueSizeBytes: Int
        get() = currentQueueSizeBytes.get()

    init {
        lastMessageHeight.set(initialLastMessageHeight)

        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("non-anchored-pipe-worker-bc-rid-$blockchainRid-topic-${route.topic}") + MDCContext()) {
            while (isActive) {
                try {
                    logger.debug { "Fetching messages" }
                    fetchMessages()
                    logger.debug { "Fetched messages" }
                } catch (_: CancellationException) {
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
        val client = clientProvider.blockchain(blockchainRid)

        val heightToQueryFrom = max(lastMessageHeight.get(), skipToHeight - 1)
        val allMessages = client.query(
                QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT,
                gtv(mapOf("topic" to gtv(route.topic), "height" to gtv(heightToQueryFrom)))
        ).asArray().map {
            val body = it["body"]!!
            val size = GtvEncoder.encodeGtv(body).size
            if (size > ICMF_MESSAGE_MAX_SIZE) throw UserMistake("Message with size $size bytes exceeds maximum size: $ICMF_MESSAGE_MAX_SIZE bytes")
            it["height"]!!.asInteger() to IcmfMessage(body, size)
        }.groupBy { it.first }.mapValues { messages -> messages.value.map { it.second } }
        logger.debug { "Fetched ${allMessages.size} messages from height $heightToQueryFrom" }

        val currentPackets = mutableListOf<Pair<IcmfPacket, Int>>()
        for ((height, messages) in allMessages) {
            val block = client.blockAtHeight(height)
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

            val packetSizeBytes = messages.sumOf { it.size }

            if (packets.isEmpty() || currentQueueSizeBytes.get() + packetSizeBytes <= maxQueueSizeBytes) {
                currentPackets.add(
                        IcmfPacket(
                                height = height,
                                sender = blockchainRid,
                                topic = route.topic,
                                blockRid = block.rid.data,
                                rawHeader = block.header.data,
                                rawWitness = block.witness.data,
                                prevMessageBlockHeight = topicData.previousBlockHeight,
                                merkleHashCalculator = makeMerkleHashCalculator(decodedHeader.getMerkleHashVersion()),
                                messages = messages
                        ) to packetSizeBytes)

                currentQueueSizeBytes.addAndGet(packetSizeBytes)
            } else {
                logger.info("pipe reached max capacity $maxQueueSizeBytes bytes")
            }
        }

        if (currentPackets.isEmpty()) return
        val highestSeenHeight = currentPackets.maxOf { it.first.height }
        val totalSizeBytes = currentPackets.sumOf { it.second }
        packets[highestSeenHeight] = IcmfPackets(highestSeenHeight, currentPackets.map { it.first }) to totalSizeBytes
        lastMessageHeight.set(highestSeenHeight)
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun haveNewPacketsForSure(): Boolean = packets.isNotEmpty()

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
