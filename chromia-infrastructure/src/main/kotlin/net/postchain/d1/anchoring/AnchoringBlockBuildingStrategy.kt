package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockBuildingStrategy
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockQueries
import java.time.Clock

// We need to keep this class to be able to sync old blockchains
@Deprecated("This logic is now included in AnchoringSpecialTxExtension")
class AnchoringBlockBuildingStrategy(configData: BaseBlockBuildingStrategyConfigurationData,
                                     blockQueries: BlockQueries,
                                     txQueue: TransactionQueue,
                                     clock: Clock) : BaseBlockBuildingStrategy(configData, blockQueries, txQueue, clock) {
     // dummy class
}
