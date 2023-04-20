package net.postchain.d1.config

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.nm_api.NodeManagement

class ManagedBlockchainConfigProvider(
        private val nodeManagement: NodeManagement,
        private val clusterManagement: ClusterManagement
) : BlockchainConfigProvider {

    companion object : KLogging()

    override fun getRelevantPeers(headerData: BlockHeaderData): Collection<PubKey> {
        val blockchainRid = BlockchainRid(headerData.getBlockchainRid())
        val height = headerData.getHeight()

        if (isPcuEnabled()) {
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

        } else {
            return clusterManagement.getBlockchainPeers(blockchainRid, height)
        }
    }

    private fun isPcuEnabled(): Boolean {
        return System.getenv("POSTCHAIN_PCU")?.let { it.toBoolean() } ?: false
    }
}