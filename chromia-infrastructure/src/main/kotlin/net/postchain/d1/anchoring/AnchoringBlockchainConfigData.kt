package net.postchain.d1.anchoring

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject

const val KEY_BLOCKCHAIN_CONFIG_ANCHORING = "anchoring"

data class AnchoringBlockchainConfigData(
        @param:Name("max_blocks_per_chain")
        @param:DefaultValue(defaultLong = 100)
        val maxBlocksPerChain: Long,
        @param:Name("max_anchoring_delay")
        @param:DefaultValue(defaultLong = 1000)
        val maxAnchoringDelay: Long,
        @param:Name("max_anchoring_blocks_per_anchor_block")
        @param:DefaultValue(defaultLong = 100)
        val maxAnchoringBlocksPerAnchorBlock: Long,
        @param:Name("batch_mode")
        @param:DefaultValue(defaultBoolean = false)
        val batchMode: Boolean
) {
    companion object {
        fun fromGtv(gtv: Gtv): AnchoringBlockchainConfigData = gtv.toObject()
    }
}
