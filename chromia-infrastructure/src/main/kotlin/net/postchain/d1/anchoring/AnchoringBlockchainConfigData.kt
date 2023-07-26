package net.postchain.d1.anchoring

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject

const val KEY_BLOCKCHAIN_CONFIG_ANCHORING = "anchoring"

data class AnchoringBlockchainConfigData(
        @Name("max_blocks_per_chain")
        @DefaultValue(defaultLong = 100)
        val maxBlocksPerChain: Long
) {
    companion object {
        fun fromGtv(gtv: Gtv): AnchoringBlockchainConfigData = gtv.toObject()
    }
}
