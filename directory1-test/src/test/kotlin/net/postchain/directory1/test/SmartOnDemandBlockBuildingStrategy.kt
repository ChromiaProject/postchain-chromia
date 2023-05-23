package net.postchain.directory1.test

import mu.KLogging
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.concurrent.util.get
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import java.util.concurrent.LinkedBlockingQueue

@Suppress("UNUSED_PARAMETER", "unused")
class SmartOnDemandBlockBuildingStrategy(
        configData: BaseBlockBuildingStrategyConfigurationData,
        blockQueries: BlockQueries,
        val txQueue: TransactionQueue
) : BlockBuildingStrategy {

    companion object : KLogging()

    @Volatile
    var upToHeight: Long = -1

    @Volatile
    var committedHeight = blockQueries.getLastBlockHeight().get().toInt()
    private val blocks = LinkedBlockingQueue<BlockData>()

    override fun shouldBuildBlock(): Boolean {
        return upToHeight > committedHeight
    }

    fun buildBlocksUpTo(height: Long) {
        upToHeight = height
    }

    override fun blockCommitted(blockData: BlockData) {
        committedHeight++
        blocks.add(blockData)
    }

    override fun blockFailed() {
    }

    fun awaitCommitted(height: Int) {
        while (committedHeight < height) {
            blocks.take()
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        return false
    }
}