package net.postchain.d1

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.d1.nm_api.NodeManagement

object BlockchainConfigProvider : KLogging() {

    fun getRelevantPeers(nodeManagement: NodeManagement, headerData: BlockHeaderData): List<PubKey> {
        val blockchainRid = BlockchainRid(headerData.getBlockchainRid())
        val height = headerData.getHeight()
        val logPrefix = "getRelevantPeers(brid: ${blockchainRid.toShortHex()}, height: $height)"

        val configHash = headerData.getExtra()["config_hash"]?.asByteArray()
        logger.debug { "$logPrefix - extra config_hash: " + configHash?.wrap() }
        if (configHash != null) {
            val pendingConfig = nodeManagement.getPendingBlockchainConfigByHash(blockchainRid, configHash)
            logger.debug { "$logPrefix - pending_signers: ${pendingConfig?.signers?.map(::PubKey)?.toTypedArray()}" }
            if (pendingConfig != null) {
                return pendingConfig.signers.map(::PubKey)
            }
        }

        val config = nodeManagement.getBlockchainConfiguration(blockchainRid, height)
                ?: throw UserMistake("Config for chain $blockchainRid not found at height $height")
        logger.debug { "$logPrefix - NM API: config.configHash: ${config.configHash}" }
        if (config.configHash == configHash?.wrap()) {
            return config.signers.map(::PubKey)
        }

        throw UserMistake("Can't find peers for chain $blockchainRid at height $height")
    }
}