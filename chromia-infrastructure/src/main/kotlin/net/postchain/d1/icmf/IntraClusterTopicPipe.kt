package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import kotlin.math.max

class IntraClusterTopicPipe(
        private val queryProvider: ChromiaQueryProvider,
        override val route: TopicRoute,
        override val id: BlockchainRid,
        private val skipToHeight: Long = 0
) : IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid> {
    companion object : KLogging()

    private val blockchainRid = id

    override fun mightHaveNewPackets(): Boolean = true

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long, IcmfPacket>? = try {
        fetchNextInternal(currentPointer)
    } catch (e: Exception) {
        logger.warn(e) { "Message fetching for $route failed: $e" }
        null
    }

    private fun fetchNextInternal(currentPointer: Long): IcmfPackets<Long, IcmfPacket>? {
        val query = queryProvider.getQuery(blockchainRid)
        if (query == null) {
            logger.warn("Unable to query blockchain-rid: ${blockchainRid.toHex()}")
            return null
        }

        val heightToQueryFrom = max(currentPointer, skipToHeight - 1)
        val allMessages = query.query(
                QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT,
                GtvFactory.gtv(mapOf("topic" to GtvFactory.gtv(route.topic), "height" to GtvFactory.gtv(heightToQueryFrom)))
        ).asArray().map {
            val size = GtvEncoder.encodeGtv(it["body"]!!).size
            if (size > MAX_MESSAGE_SIZE) throw UserMistake("Message with size $size bytes exceeds maximum size: $MAX_MESSAGE_SIZE bytes")
            it["height"]!!.asInteger() to IcmfMessage(it["body"]!!, size)
        }.groupBy { it.first }.mapValues { messages -> messages.value.map { it.second } }

        val packets = mutableListOf<IcmfPacket>()
        for ((height, messages) in allMessages) {
            val block = query.blockAtHeight(height)
            if (block == null) {
                logger.warn("Unable to fetch block at height: $height from blockchain-rid: ${blockchainRid.toHex()}")
                return null
            }

            val decodedHeader = BlockHeaderData.fromBinary(block.header.data)
            val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
            if (icmfHeaderData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: ${block.rid.toHex()} for blockchain-rid: ${blockchainRid.toHex()} at height: $height")
                return null
            }

            val topicData = icmfHeaderData[route.topic]?.let { TopicHeaderData.fromGtv(it) }
            if (topicData == null) {
                logger.warn(
                        "$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${block.rid.toHex()} for blockchain-rid: ${
                            blockchainRid.toHex()
                        } at height: $height"
                )
                return null
            }

            packets.add(
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

        if (packets.isEmpty()) return null
        return IcmfPackets(packets.maxOf { it.height }, packets)
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {}

    override fun shutdown() {}
}
