package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockBuildingStrategy
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.base.withReadConnection
import net.postchain.core.Storage
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries

class AnchoringBlockBuildingStrategy(configData: BaseBlockBuildingStrategyConfigurationData,
                                     blockQueries: BlockQueries,
                                     txQueue: TransactionQueue) : BaseBlockBuildingStrategy(configData, blockQueries, txQueue) {

    var chainId: Long = -1
    lateinit var blockBuilderStorage: Storage
    lateinit var txExtension: AnchoringSpecialTxExtension

    override fun extendedShouldBuildBlock(): Boolean {
        if (!::txExtension.isInitialized) return false

        return withReadConnection(blockBuilderStorage, chainId) { ectx ->
            txExtension.hasBlocksToAnchor(ectx)
        }
    }
}