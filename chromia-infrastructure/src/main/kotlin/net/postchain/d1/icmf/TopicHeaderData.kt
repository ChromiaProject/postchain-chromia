package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.Validation
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

data class TopicHeaderData(val hash: ByteArray, val prevMessageBlockHeight: Long) {
    companion object : KLogging() {

        fun extractTopicHeaderData(header: BlockHeaderData,
                                   rawHeader: ByteArray,
                                   rawWitness: ByteArray,
                                   blockRid: ByteArray,
                                   cryptoSystem: CryptoSystem,
                                   clusterManagement: ClusterManagement): Map<String, TopicHeaderData>? {
            val chainPeers = clusterManagement.getBlockchainPeers(BlockchainRid(header.getBlockchainRid()), header.getHeight())
            val witness = BaseBlockWitness.fromBytes(rawWitness)

            if (!Validation.validateBlockSignatures(cryptoSystem, header.getPreviousBlockRid(), rawHeader, blockRid, chainPeers, witness)) {
                logger.warn("Invalid block header signature for block-rid: ${blockRid.toHex()} for blockchain-rid: ${header.getBlockchainRid().toHex()} at height: ${header.getHeight()}")
                return null
            }

            val icmfHeaderData = header.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
            if (icmfHeaderData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: ${blockRid.toHex()} for blockchain-rid: ${header.getBlockchainRid().toHex()} at height: ${header.getHeight()}")
                return null
            }

            return icmfHeaderData.asDict().mapValues { fromGtv(it.value) }
        }

        fun fromGtv(gtv: Gtv): TopicHeaderData = TopicHeaderData(gtv["hash"]!!.asByteArray(), gtv["prev_message_block_height"]!!.asInteger())

        fun fromMessages(messages: List<Gtv>, cryptoSystem: CryptoSystem, prevMessageBlockHeight: Long): TopicHeaderData =
                TopicHeaderData(calculateMessagesHash(messages, cryptoSystem), prevMessageBlockHeight)

        fun calculateMessagesHash(messages: List<Gtv>, cryptoSystem: CryptoSystem): ByteArray =
                gtv(messages).merkleHash(GtvMerkleHashCalculator(cryptoSystem))
    }

    fun toGtv(): Gtv = gtv("hash" to gtv(hash), "prev_message_block_height" to gtv(prevMessageBlockHeight))
}
