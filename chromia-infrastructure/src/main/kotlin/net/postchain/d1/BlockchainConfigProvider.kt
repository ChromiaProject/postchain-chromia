package net.postchain.d1

import mu.KLogging
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.nm_api.NodeManagement
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

object BlockchainConfigProvider : KLogging() {

    fun getRelevantPeers(nodeManagement: NodeManagement, headerData: BlockHeaderData): List<PubKey> {
        logger.error { "===== getRelevantPeers =====" }
        val blockchainRid = BlockchainRid(headerData.getBlockchainRid())
        val configHash = headerData.getExtra()["config_hash"]?.asByteArray()
        logger.error { "~~~~~~~ configHash: " + configHash?.wrap() }
        if (configHash != null) {
            val pendingConfig = nodeManagement.getPendingBlockchainConfigByHash(blockchainRid, configHash)
            logger.error { "~~~~~~~ pendingSigners: " + pendingConfig?.signers?.map(::PubKey)?.toTypedArray()?.contentToString() }
            if (pendingConfig != null) {
                return pendingConfig.signers.map(::PubKey)
            }
        }

        val config = nodeManagement.getBlockchainConfiguration(blockchainRid, headerData.getHeight())
                ?: throw UserMistake("Config for chain $blockchainRid not found at height ${headerData.getHeight()}")
        logger.error { "~~~~~~~ config.configHash: " + config.configHash }
        if (config.configHash == configHash?.wrap()) {
            return config.signers.map(::PubKey)
        }

        // TODO: will be removed/refactored
        val config0 = nodeManagement.getBlockchainConfiguration(blockchainRid, 0)!!
        val fullConfig0 = GtvFactory.decodeGtv(config0.baseConfig.data).asDict().toMutableMap()
        fullConfig0[KEY_SIGNERS] = gtv(config0.signers.map { gtv(it.data) })
        val configHash0 = gtv(fullConfig0).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem())).wrap()
        logger.error { "~~~~~~~~~ configHash0: " + configHash0 }
        if (configHash0 == configHash?.wrap()) {
            return config.signers.map(::PubKey)
        }

        return config.signers.map(::PubKey) // TODO: Hmm, why does this work?
//        throw UserMistake("Can't find peers for chain $blockchainRid at height ${headerData.getHeight()}")
    }

}