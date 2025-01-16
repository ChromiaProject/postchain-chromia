package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.extension.getMerkleHashVersion
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.d1.rell.anchoring_chain_cluster.icmfGetHeadersWithMessagesAfterHeight
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtv.merkleHash

class IntraClusterAnchoredTopicPipe(
        private val queryProvider: ChromiaQueryProvider,
        override val route: TopicRoute,
        override val id: String,
        private val clusterManagement: ClusterManagement
) : IcmfPipe<TopicRoute, Long, IcmfAnchorPacket, String>, Shutdownable {
    companion object : KLogging()

    private val clusterName = id

    override fun mightHaveNewPackets(): Boolean = true

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long, IcmfAnchorPacket>? = try {
        fetchNextInternal(currentPointer)
    } catch (e: Exception) {
        logger.warn(e) { "Message fetching for $route failed: $e" }
        null
    }

    private fun fetchNextInternal(currentPointer: Long): IcmfPackets<Long, IcmfAnchorPacket>? {
        val anchorQuery = queryProvider.getClusterAnchoringQuery()
        if (anchorQuery == null) {
            logger.warn("Anchor chain does not exist!")
            return null
        }

        val signedBlockHeaderWithAnchorHeights = anchorQuery.icmfGetHeadersWithMessagesAfterHeight(
                route.topic,
                currentPointer
        )

        val anchorPackets = mutableListOf<IcmfAnchorPacket>()
        var maxAnchorHeight = currentPointer
        for ((anchorHeight, headers) in signedBlockHeaderWithAnchorHeights.groupBy { it.anchorHeight }.toList().sortedBy { it.first }) {
            val anchorBlock = anchorQuery.blockAtHeight(anchorHeight)
            if (anchorBlock == null) {
                logger.warn("Anchor block at height $anchorHeight not found")
                return null
            }

            val packets = mutableListOf<IcmfPacket>()
            for (header in headers) {
                val decodedHeader = BlockHeaderData.fromBinary(header.blockHeader.data)
                val blockchainRid = BlockchainRid(decodedHeader.getBlockchainRid())

                if (route.chains.isNotEmpty() && !route.chains.contains(blockchainRid)) {
                    continue // we only read from specific chains
                }

                if (header.anchorHeight > maxAnchorHeight) maxAnchorHeight = header.anchorHeight

                val query = queryProvider.getQuery(blockchainRid)
                if (query == null) {
                    if (!clusterManagement.getActiveBlockchains(clusterName).contains(blockchainRid)) {
                        logger.info("Blockchain with blockchain-rid: ${blockchainRid.toHex()} is permanently stopped")
                    } else {
                        logger.warn("Cannot find blockchain with blockchain-rid: ${blockchainRid.toHex()}, will retry later")
                    }
                    continue
                }

                val messages = query.query(
                        QUERY_ICMF_GET_MESSAGES_AT_HEIGHT,
                        GtvFactory.gtv(mapOf("topic" to GtvFactory.gtv(route.topic), "height" to GtvFactory.gtv(decodedHeader.getHeight())))
                ).asArray().map {
                    val size = GtvEncoder.encodeGtv(it).size
                    if (size > MAX_MESSAGE_SIZE) throw UserMistake("Message with size $size bytes exceeds maximum size: $MAX_MESSAGE_SIZE bytes")
                    IcmfMessage(it, size)
                }

                val merkleHashCalculator = makeMerkleHashCalculator(decodedHeader.getMerkleHashVersion())
                val blockRid = decodedHeader.toGtv().merkleHash(merkleHashCalculator)

                val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
                if (icmfHeaderData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: ${blockRid.toHex()} for blockchain-rid: ${blockchainRid.toHex()} at height: ${decodedHeader.getHeight()}")
                    continue
                }

                val topicData = icmfHeaderData[route.topic]?.let { TopicHeaderData.fromGtv(it) }
                if (topicData == null) {
                    logger.warn(
                            "$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${blockRid.toHex()} for blockchain-rid: ${
                                blockchainRid.toHex()
                            } at height: ${decodedHeader.getHeight()}"
                    )
                    continue
                }

                packets.add(
                        IcmfPacket(
                                height = decodedHeader.getHeight(),
                                sender = blockchainRid,
                                topic = route.topic,
                                blockRid = blockRid,
                                rawHeader = header.blockHeader.data,
                                rawWitness = header.witness.data,
                                prevMessageBlockHeight = topicData.previousBlockHeight,
                                merkleHashCalculator = merkleHashCalculator,
                                messages = messages
                        )
                )
            }

            anchorPackets.add(IcmfAnchorPacket(
                    anchorBlock.header.data,
                    anchorBlock.witness.data,
                    anchorHeight,
                    packets
            ))
        }
        return if (anchorPackets.isEmpty()) null else IcmfPackets(maxAnchorHeight, anchorPackets)
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {}

    override fun shutdown() {}
}
