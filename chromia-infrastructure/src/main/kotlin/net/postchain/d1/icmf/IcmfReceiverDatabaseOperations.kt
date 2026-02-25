package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

interface IcmfReceiverDatabaseOperations {
    fun initialize(ctx: EContext)
    fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long
    fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight>
    fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long)
    fun saveLastAnchoredHeights(ctx: EContext, anchorHeights: List<AnchorHeight>)
    fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender>
    fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long
    fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long)
    fun saveLastMessageHeights(ctx: EContext, messageHeights: List<MessageHeightForSender>)
    fun loadOldestSpilledMessage(ctx: EContext, sender: BlockchainRid, topic: String): SpilledMessage?
    fun loadSpilledMessageCounts(ctx: EContext, cluster: String, anchorHeight: Long, topic: String): Map<BlockchainRid, Int>
    fun saveSpilledMessages(ctx: EContext, spilledMessages: List<SpilledMessageWithSenderAndTopic>)
    fun loadSpilledMessageStates(ctx: EContext): List<SpilledMessageState>
    fun imprecateSpilledMessage(ctx: EContext, serial: Long)
    fun deleteDappProvidedReceiverTopics(ctx: EContext)
    fun saveDappProvidedReceiverTopics(ctx: EContext, topics: List<IcmfReceiverEventTopic>)
    fun deleteDappProvidedReceiverTopics(ctx: EContext, topics: List<IcmfReceiverEventTopic>)
    fun loadDappProvidedReceiverTopics(ctx: EContext): List<IcmfReceiverEventTopic>
}

data class AnchorHeight(
        val cluster: String,
        val topic: String,
        val height: Long
) {
    fun toGtv(): Gtv = gtv(listOf(
            gtv(cluster),
            gtv(topic),
            gtv(height)
    ))
}

data class MessageHeightForSender(
        val sender: BlockchainRid,
        val topic: String,
        val height: Long
) {
    fun toGtv(): Gtv = gtv(listOf(
            gtv(sender),
            gtv(topic),
            gtv(height)
    ))
}

data class SpilledMessage(
        val serial: Long,
        val hash: ByteArray,
        val cluster: String,
        val anchorHeight: Long,
        val merkleHashVersion: Long
)

data class SpilledMessageWithSenderAndTopic(
        val spillHeight: Long,
        val topic: String,
        val sender: BlockchainRid,
        val cluster: String,
        val anchorHeight: Long,
        val merkleHashVersion: Long,
        val hash: ByteArray,
)

data class SpilledMessageState(
        val spillHeight: Long,
        val topic: String,
        val sender: BlockchainRid,
        val oldestSpilledMessageHash: ByteArray,
) {
    fun toGtv(): Gtv = gtv(listOf(
            gtv(spillHeight),
            gtv(topic),
            gtv(sender),
            gtv(oldestSpilledMessageHash)
    ))
}
