package net.postchain.d1

import mu.KotlinLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.d1.config.BlockchainConfigProvider

private val logger = KotlinLogging.logger {}

fun getCachedPeers(
        peerCache: MutableMap<BlockchainRid, Pair<ByteArray, Collection<PubKey>>>,
        headerData: BlockHeaderData,
        blockchainConfigProvider: BlockchainConfigProvider
): Collection<PubKey>? {
    val bcRid = BlockchainRid(headerData.getBlockchainRid())
    val configHash = headerData.getExtra()["config_hash"]?.asByteArray()

    if (configHash != null) {
        peerCache[bcRid]?.let { (cachedConfigHash, peers) ->
            if (cachedConfigHash.contentEquals(configHash)) {
                logger.debug { "Retrieved cached peers for blockchain: $bcRid and config hash: ${configHash.toHex()}" }
                return peers
            }
        }
    }

    try {
        val peers = blockchainConfigProvider.getRelevantPeers(headerData)

        if (configHash != null) {
            peerCache[bcRid] = configHash to peers
        }
        return peers
    } catch (e: UserMistake) {
        logger.warn(e.message)
        return null
    }
}
