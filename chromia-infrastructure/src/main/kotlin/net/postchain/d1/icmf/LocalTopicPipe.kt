package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.d1.rell.anchor.icmfGetHeadersWithMessagesAfterHeight
import net.postchain.d1.rell.icmf.icmfGetMessages
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

class LocalTopicPipe(
    private val queryProvider: ChromiaQueryProvider,
    override val route: TopicRoute,
    override val id: String,
    private val cryptoSystem: CryptoSystem,
    private val clusterManagement: ClusterManagement
) : IcmfPipe<TopicRoute, Long, String>, Shutdownable {
    companion object : KLogging()

    private val clusterName = id

    override fun mightHaveNewPackets(): Boolean = true

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long>? {
        val anchorQuery = queryProvider.getAnchorQuery()
        if (anchorQuery == null) {
            logger.warn("Anchor chain does not exist!")
            return null
        }

        val packets = mutableListOf<IcmfPacket>()

        val signedBlockHeaderWithAnchorHeights = anchorQuery.icmfGetHeadersWithMessagesAfterHeight(
            route.topic,
            currentPointer
        )

        var maxAnchorHeight = currentPointer
        for (header in signedBlockHeaderWithAnchorHeights) {
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

            val bodies = query.icmfGetMessages(
                route.topic,
                decodedHeader.getHeight()
            )

            val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))

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
                    prevMessageBlockHeight = topicData.prevMessageBlockHeight,
                    bodies = bodies
                )
            )
        }

        return if (packets.isEmpty()) null else IcmfPackets(maxAnchorHeight, packets)
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {}

    override fun shutdown() {}
}
