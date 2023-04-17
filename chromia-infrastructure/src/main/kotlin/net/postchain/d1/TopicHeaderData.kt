package net.postchain.d1

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.nm_api.NodeManagement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class TopicHeaderData(val hash: ByteArray, val previousBlockHeight: Long) {
    companion object : KLogging() {
        fun extractTopicHeaderData(
                header: BlockHeaderData,
                rawHeader: ByteArray,
                rawWitness: ByteArray,
                blockRid: ByteArray,
                cryptoSystem: CryptoSystem,
                nodeManagement: NodeManagement,
                extraField: String
        ): Map<String, TopicHeaderData>? {
            val witness = BaseBlockWitness.fromBytes(rawWitness)
            val peers = BlockchainConfigProvider.getRelevantPeers(nodeManagement, header)

            try {
                Validation.validateBlockSignatures(cryptoSystem, header.getPreviousBlockRid(), rawHeader, blockRid, peers, witness)
            } catch (e: UserMistake) {
                logger.warn("Invalid block header signature when extracting data from extra data '$extraField' for block-rid: ${blockRid.toHex()} in blockchain: ${header.getBlockchainRid().toHex()} at height: ${header.getHeight()}: ${e.message}")
                return null
            }

            val extraData = header.getExtra()[extraField]
            if (extraData == null) {
                logger.warn("'$extraField' extra data missing for block-rid: ${blockRid.toHex()} in blockchain: ${header.getBlockchainRid().toHex()} at height: ${header.getHeight()}")
                return null
            }

            return extraData.asDict().mapValues { fromGtv(it.value) }
        }

        fun fromGtv(gtv: Gtv): TopicHeaderData = TopicHeaderData(gtv["hash"]!!.asByteArray(), gtv["previous_block_height"]!!.asInteger())
    }

    fun toGtv(): Gtv = gtv("hash" to gtv(hash), "previous_block_height" to gtv(previousBlockHeight))
}
