package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface IcmfDatabaseOperations {
    fun initialize(ctx: EContext)
    fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long
    fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight>
    fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long)
    fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender>
    fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long
    fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long)
    fun loadOldestSpilledMessage(ctx: EContext, sender: BlockchainRid, topic: String): SpilledMessage?
    fun loadSpilledMessageCounts(ctx: EContext, cluster: String, anchorHeight: Long, topic: String): Map<BlockchainRid, Int>
    fun saveSpilledMessage(ctx: EContext, cluster: String, anchorHeight: Long, sender: BlockchainRid, topic: String, hash: ByteArray)
    fun imprecateSpilledMessage(ctx: EContext, serial: Long)
    fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray)
    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, blockHeight: Long): Long
    fun getSentMessagesAfterHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
    fun getSentMessagesAtHeight(ctx: EContext, topic: String, blockHeight: Long): List<Gtv>
    fun saveDappProvidedReceiverTopics(ctx: EContext, cluster: String, receiver: ByteArray, topics: List<IcmfReceiverTopicEventMessage>)
    fun loadDappProvidedReceiverTopics(ctx: EContext, cluster: String, receiver: ByteArray): List<IcmfReceiverTopicEventMessage>
}

data class AnchorHeight(
        val cluster: String,
        val topic: String,
        val height: Long
)

data class MessageHeightForSender(
        val sender: BlockchainRid,
        val topic: String,
        val height: Long
)

data class SpilledMessage(
        val serial: Long,
        val hash: ByteArray,
        val cluster: String,
        val anchorHeight: Long
)

data class IcmfMessageAtHeight(
        val height: Long,
        val body: Gtv
)