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
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.rell.anchor.icmfGetHeadersWithMessagesAfterHeight
import net.postchain.d1.rell.icmf.icmfGetMessages
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class ClusterGlobalTopicPipe(override val route: TopicRoute,
                             override val id: String,
                             private val cryptoSystem: CryptoSystem,
                             lastAnchorHeight: Long,
                             private val clientProvider: ChromiaClientProvider,
                             private val clusterManagement: ClusterManagement,
                             _lastMessageHeights: List<Pair<BlockchainRid, Long>>) : IcmfPipe<TopicRoute, Long, String>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 10.seconds
        const val maxQueueSizeBytes = 10 * 1024 * 1024 // 10 MiB
    }

    private val clusterName = id
    private val packets = ConcurrentSkipListMap<Long, Pair<IcmfPackets<Long>, Int>>()
    private val currentQueueSizeBytes = AtomicInteger(0)
    private val lastAnchorHeight = AtomicLong(lastAnchorHeight)
    private val lastMessageHeights: ConcurrentMap<BlockchainRid, Long> = ConcurrentHashMap()
    private val job: Job

    init {
        _lastMessageHeights.forEach { lastMessageHeights[it.first] = it.second }

        job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("pipe-worker-cluster-$clusterName-topic-${route.topic}")) {
            while (isActive) {
                try {
                    logger.info("Fetching messages")
                    fetchMessages()
                    logger.info("Fetched messages")
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error("Message fetch failed: ${e.message}", e)
                }
                delay(pollInterval)
            }
        }
    }

    private suspend fun fetchMessages() {
        val cluster = clusterManagement.getClusterInfo(clusterName)

        val clusterClient = clientProvider.cluster(clusterName)
        val anchoringClient = clusterClient.blockchain(cluster.anchoringChain)

        val currentPackets = mutableListOf<IcmfPacket>()

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

        var maxAnchorHeight = fromAnchorHeight
        for (header in signedBlockHeaderWithAnchorHeights) {
            val decodedHeader = BlockHeaderData.fromBinary(header.blockHeader.data)
            val blockchainRid = BlockchainRid(decodedHeader.getBlockchainRid())

            if (route.chains.isNotEmpty() && !route.chains.contains(blockchainRid)) {
                continue // we only read from specific chains
            }

            val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            val topicHeaderData = TopicHeaderData.extractTopicHeaderData(decodedHeader, header.blockHeader.data, header.witness.data, blockRid, cryptoSystem, clusterManagement)
                    ?: return

            val topicData = topicHeaderData[route.topic]
            if (topicData == null) {
                logger.warn(
                    "$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${blockRid.toHex()} for blockchain-rid: ${
                        blockchainRid.toHex()
                    } at height: ${decodedHeader.getHeight()}")
                return
            }

            val currentPrevMessageBlockHeight = lastMessageHeights[BlockchainRid(decodedHeader.getPreviousBlockRid())]
                    ?: -1
            if (decodedHeader.getHeight() <= currentPrevMessageBlockHeight) {
                continue // already processed in previous block, skip it here
            } else if (topicData.prevMessageBlockHeight != currentPrevMessageBlockHeight) {
                logger.warn(
                    "$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height ${topicData.prevMessageBlockHeight}, expected $currentPrevMessageBlockHeight for sender ${
                        blockchainRid.toHex()
                    }")
                return
            }

            if (header.anchorHeight > maxAnchorHeight) maxAnchorHeight = header.anchorHeight

            val bodies = fetchMessageBodies(clusterClient, blockchainRid, decodedHeader.getHeight(), topicData.hash)

            if (bodies.isNotEmpty()) {
                currentPackets.add(
                        IcmfPacket(
                                height = decodedHeader.getHeight(),
                                sender = blockchainRid,
                                topic = route.topic,
                                blockRid = blockRid,
                                rawHeader = header.blockHeader.data,
                                rawWitness = header.witness.data,
                                prevMessageBlockHeight = topicData.prevMessageBlockHeight,
                                bodies = bodies
                        )
                )
            }
            lastMessageHeights[BlockchainRid(decodedHeader.getPreviousBlockRid())] = decodedHeader.getHeight()
        }

        val packetsSizeBytes = currentPackets.sumOf { it.bodies.sumOf { body -> GtvEncoder.encodeGtv(body).size } }
        if (packets.isEmpty() || currentQueueSizeBytes.get() + packetsSizeBytes <= maxQueueSizeBytes) {
            packets[maxAnchorHeight] = IcmfPackets(maxAnchorHeight, currentPackets) to packetsSizeBytes
            currentQueueSizeBytes.addAndGet(packetsSizeBytes)

            lastAnchorHeight.set(maxAnchorHeight)
        } else {
            logger.info("pipe reached max capacity $maxQueueSizeBytes bytes")
        }
    }

    private suspend fun fetchMessageBodies(clusterClient: ChromiaClientProvider.ClusterPostchainClient, blockchainRid: BlockchainRid, height: Long, expectedMessagesHash: ByteArray): List<Gtv> {
        val client = clusterClient.blockchain(blockchainRid)

        while (true) {
            logger.info("Fetching messages from ${blockchainRid.toHex()} at height $height")
            val bodies = try {
                client.icmfGetMessages(route.topic, height)
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

            val computedHash = TopicHeaderData.calculateMessagesHash(bodies.map { cryptoSystem.digest(GtvEncoder.encodeGtv(it)) }, cryptoSystem)

            if (!expectedMessagesHash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for blockchain-rid: ${blockchainRid.toHex()} at height: $height, will retry after $pollInterval")
                delay(pollInterval)
            } else {
                return bodies
            }
        }
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long>? =
            packets.higherEntry(currentPointer)?.value?.first

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            packets.remove(currentPointer)?.let {
                currentQueueSizeBytes.addAndGet(-it.second)
            }
        }
    }

    override fun shutdown() {
        job.cancel()
    }
}
