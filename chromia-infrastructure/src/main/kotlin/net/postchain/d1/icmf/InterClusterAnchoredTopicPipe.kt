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
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.rell.anchoring_chain_cluster.icmfGetHeadersWithMessagesAfterHeight
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class InterClusterAnchoredTopicPipe(override val route: TopicRoute,
                                    override val id: String,
                                    private val cryptoSystem: CryptoSystem,
                                    lastAnchorHeight: Long,
                                    private val clientProvider: ChromiaClientProvider,
                                    private val clusterManagement: ClusterManagement,
                                    private val blockchainConfigProvider: BlockchainConfigProvider,
                                    _lastMessageHeights: List<Pair<BlockchainRid, Long>>
) : IcmfPipe<TopicRoute, Long, IcmfAnchorPacket, String>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 10.seconds
        const val maxQueueSizeBytes = 32 * 1024 * 1024 // 32 MiB
    }

    private val clusterName = id
    private val packets = ConcurrentSkipListMap<Long, Pair<IcmfPackets<Long, IcmfAnchorPacket>, Int>>()
    private val currentQueueSizeBytes = AtomicInteger(0)
    private val lastAnchorHeight = AtomicLong(lastAnchorHeight)
    private val lastMessageHeights: ConcurrentMap<BlockchainRid, Long> = ConcurrentHashMap()
    private val job: Job

    internal val queueIsEmpty: Boolean
        get() = currentQueueSizeBytes.get() == 0

    init {
        _lastMessageHeights.forEach { lastMessageHeights[it.first] = it.second }

        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("anchored-pipe-worker-cluster-$clusterName-topic-${route.topic}") + MDCContext()) {
            while (isActive) {
                try {
                    logger.debug { "Fetching messages" }
                    fetchMessages()
                    logger.debug { "Fetched messages" }
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

    private suspend fun fetchMessages() {
        val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

        val cluster = clusterManagement.getClusterInfo(clusterName)

        val clusterClient = clientProvider.cluster(clusterName)
        val anchoringClient = clusterClient.blockchain(cluster.anchoringChain)

        val fromAnchorHeight = lastAnchorHeight.get()
        val signedBlockHeaderWithAnchorHeights = try {
            anchoringClient.icmfGetHeadersWithMessagesAfterHeight(route.topic, fromAnchorHeight)
        } catch (e: Exception) {
            when (e) {
                is UserMistake, is IOException -> {
                    logger.warn("Unable to query for messages on anchor chain: ${e.message}", e)
                    return
                }

                else -> throw e
            }
        }

        val decodedBlockHeaderWithAnchorHeights = signedBlockHeaderWithAnchorHeights.map {
            val decodedHeader = BlockHeaderData.fromBinary(it.blockHeader.data)
            val blockRid = decodedHeader.toGtv().merkleHash(merkleHashCalculator)
            DecodedBlockHeaderWithAnchorHeight(it.blockHeader.data, it.witness.data, it.anchorHeight, decodedHeader, blockRid)
        }

        var lastSeenAnchorHeight = fromAnchorHeight
        val icmfAnchorPackets = mutableListOf<IcmfAnchorPacket>()
        for ((anchorHeight, headers) in decodedBlockHeaderWithAnchorHeights.groupBy { it.anchorHeight }.toList().sortedBy { it.first }) {
            val hash = gtv(headers.map { gtv(it.blockRid) }).merkleHash(merkleHashCalculator)
            val anchorBlock = try {
                anchoringClient.blockAtHeight(anchorHeight)
            } catch (e: Exception) {
                when (e) {
                    is UserMistake, is IOException -> {
                        logger.warn("Unable to fetch block at height $anchorHeight on anchor chain: ${e.message}", e)
                        return
                    }

                    else -> throw e
                }
            }
            if (anchorBlock == null) {
                logger.warn("Anchor block at height $anchorHeight not found")
                return
            }

            val decodedAnchorHeader = BlockHeaderData.fromBinary(anchorBlock.header.data)
            val blockRid = decodedAnchorHeader.toGtv().merkleHash(merkleHashCalculator)

            val anchorExtraData = TopicHeaderData.extractTopicHeaderData(decodedAnchorHeader, anchorBlock.header.data, anchorBlock.witness.data, blockRid, cryptoSystem, blockchainConfigProvider, ICMF_ANCHOR_HEADERS_EXTRA)
                    ?: return

            val anchorHeaderData = anchorExtraData[route.topic]
            if (anchorHeaderData == null) {
                logger.warn("Anchor block extra header missing topic ${route.topic} for block-rid: ${blockRid.toHex()} at height: $anchorHeight")
                return
            }

            if (!anchorHeaderData.hash.contentEquals(hash)) {
                logger.warn("Anchor block header has wrong hash for block-rid: ${blockRid.toHex()} at height: $anchorHeight, expected ${hash.toHex()} but was ${anchorHeaderData.hash.toHex()}")
                return
            }

            if (anchorHeaderData.previousBlockHeight != lastSeenAnchorHeight) {
                logger.warn("Anchor block header has wrong previous height for block-rid: ${blockRid.toHex()} at height: $anchorHeight, expected $lastSeenAnchorHeight but was ${anchorHeaderData.previousBlockHeight}")
                return
            }

            val icmfPackets = mutableListOf<IcmfPacket>()
            for (header in headers) {
                val blockchainRid = BlockchainRid(header.decodedHeader.getBlockchainRid())

                if (route.chains.isNotEmpty() && !route.chains.contains(blockchainRid)) {
                    continue // we only read from specific chains
                }

                val topicHeaderData = TopicHeaderData.extractTopicHeaderData(header.decodedHeader, header.blockHeader, header.witness, header.blockRid, cryptoSystem, blockchainConfigProvider, ICMF_BLOCK_HEADER_EXTRA)
                        ?: return

                val topicData = topicHeaderData[route.topic]
                if (topicData == null) {
                    logger.warn(
                            "$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${header.blockRid.toHex()} for blockchain-rid: ${
                                blockchainRid.toHex()
                            } at height: ${header.decodedHeader.getHeight()}")
                    return
                }

                val currentPrevMessageBlockHeight = lastMessageHeights[BlockchainRid(header.decodedHeader.getPreviousBlockRid())]
                        ?: -1
                if (header.decodedHeader.getHeight() <= currentPrevMessageBlockHeight) {
                    continue // already processed in previous block, skip it here
                } else if (topicData.previousBlockHeight != currentPrevMessageBlockHeight) {
                    logger.warn(
                            "$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height ${topicData.previousBlockHeight}, expected $currentPrevMessageBlockHeight for sender ${
                                blockchainRid.toHex()
                            }")
                    return
                }

                val messages = fetchMessages(clusterClient, blockchainRid, header.decodedHeader.getHeight(), topicData.hash)

                if (messages.isNotEmpty()) {
                    icmfPackets.add(
                            IcmfPacket(
                                    height = header.decodedHeader.getHeight(),
                                    sender = blockchainRid,
                                    topic = route.topic,
                                    blockRid = header.blockRid,
                                    rawHeader = header.blockHeader,
                                    rawWitness = header.witness,
                                    prevMessageBlockHeight = topicData.previousBlockHeight,
                                    messages = messages
                            )
                    )
                }
                lastMessageHeights[BlockchainRid(header.decodedHeader.getPreviousBlockRid())] = header.decodedHeader.getHeight()
            }

            icmfAnchorPackets.add(
                    IcmfAnchorPacket(
                            anchorBlock.header.data,
                            anchorBlock.witness.data,
                            anchorHeight,
                            icmfPackets
                    )
            )

            lastSeenAnchorHeight = anchorHeight
        }
        // No new packets we can return
        if (icmfAnchorPackets.all { it.packets.isEmpty() }) return

        val packetsSizeBytes = icmfAnchorPackets.sumOf { anchorPacket ->
            anchorPacket.packets.sumOf { packet ->
                packet.messages.sumOf { it.size }
            }
        }
        if (packets.isEmpty() || currentQueueSizeBytes.get() + packetsSizeBytes <= maxQueueSizeBytes) {
            packets[lastSeenAnchorHeight] = IcmfPackets(lastSeenAnchorHeight, icmfAnchorPackets) to packetsSizeBytes
            currentQueueSizeBytes.addAndGet(packetsSizeBytes)

            lastAnchorHeight.set(lastSeenAnchorHeight)
        } else {
            logger.info("pipe reached max capacity $maxQueueSizeBytes bytes")
        }
    }

    private suspend fun fetchMessages(clusterClient: ChromiaClientProvider.ClusterPostchainClient,
                                      blockchainRid: BlockchainRid,
                                      height: Long,
                                      expectedMessagesHash: ByteArray): List<IcmfMessage> {
        val client = clusterClient.blockchain(blockchainRid)

        while (true) {
            logger.info("Fetching messages from ${blockchainRid.toHex()} at height $height")
            val bodies = try {
                client.query(QUERY_ICMF_GET_MESSAGES_AT_HEIGHT, gtv(mapOf("topic" to gtv(route.topic), "height" to gtv(height)))).asArray()
            } catch (e: Exception) {
                when (e) {
                    is UserMistake, is IOException -> {
                        if (!clusterManagement.getActiveBlockchains(clusterName).contains(blockchainRid)) {
                            // chain is permanently stopped
                            logger.info("Blockchain with blockchain-rid: ${blockchainRid.toHex()} is permanently stopped: ${e.message}")
                            return emptyList()
                        }
                        logger.warn(
                                "Unable to query blockchain with blockchain-rid: ${blockchainRid.toHex()} for messages: ${e.message}, will retry after $pollInterval",
                                e
                        )
                        delay(pollInterval)
                        continue
                    }

                    else -> throw e
                }
            }

            val messages = bodies.map {
                val size = GtvEncoder.encodeGtv(it).size
                if (size > MAX_MESSAGE_SIZE) throw UserMistake("Message with size $size bytes exceeds maximum size: $MAX_MESSAGE_SIZE bytes")
                IcmfMessage(it, size)
            }

            val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
            val computedHash = gtv(bodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator)

            if (!expectedMessagesHash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for blockchain-rid: ${blockchainRid.toHex()} at height: $height, will retry after $pollInterval")
                delay(pollInterval)
            } else {
                return messages
            }
        }
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long, IcmfAnchorPacket>? =
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

    data class DecodedBlockHeaderWithAnchorHeight(
            val blockHeader: ByteArray,
            val witness: ByteArray,
            val anchorHeight: Long,
            val decodedHeader: BlockHeaderData,
            val blockRid: ByteArray
    )
}
