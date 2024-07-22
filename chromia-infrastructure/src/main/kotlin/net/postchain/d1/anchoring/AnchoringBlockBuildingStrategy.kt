package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.base.BaseBlockBuildingStrategy
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.time.Clock

class AnchoringBlockBuildingStrategy(configData: BaseBlockBuildingStrategyConfigurationData,
                                     blockQueries: BlockQueries,
                                     txQueue: TransactionQueue,
                                     private val clock: Clock) : BaseBlockBuildingStrategy(configData, blockQueries, txQueue, clock) {


    companion object : KLogging()

    lateinit var txExtension: AnchoringSpecialTxExtension
    lateinit var anchoringConfig: AnchoringBlockchainConfigData

    private var firstAnchorBlockTime = 0L

    override fun preemptiveBlockBuilding(): Boolean = false

    override fun blockCommitted(blockData: BlockData) {
        firstAnchorBlockTime = 0
        super.blockCommitted(blockData)
    }

    override fun extendedShouldBuildBlock(): Boolean {
        if (!::txExtension.isInitialized) return false

        val now = clock.millis()
        if (firstAnchorBlockTime > 0 && now - firstAnchorBlockTime > anchoringConfig.maxAnchoringDelay) return true

        val numberOfBlocksToAnchor: Long = try {
            txExtension.numberOfBlocksToAnchor()
        } catch (e: Exception) {
            logger.error("Could not fetch number of blocks to anchor", e)
            return false
        }
        if (numberOfBlocksToAnchor >= anchoringConfig.maxAnchoringBlocksPerAnchorBlock) return true
        if (firstAnchorBlockTime == 0L && numberOfBlocksToAnchor > 0) {
            firstAnchorBlockTime = now
            return false
        }

        return false
    }
}