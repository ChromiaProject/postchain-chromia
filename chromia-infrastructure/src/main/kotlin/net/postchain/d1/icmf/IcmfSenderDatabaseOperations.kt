package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface IcmfSenderDatabaseOperations {
    fun initialize(ctx: EContext)
    fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray): Long
    fun saveSentMessagesWithId(ctx: EContext, messages: List<SentIcmfMessageData>)
    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, blockHeight: Long): Long
    fun getSentMessagesAfterHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
    fun getSentMessagesAtHeight(ctx: EContext, topic: String, blockHeight: Long): List<Gtv>
    fun getAllTopics(ctx: EContext): List<String>
    fun getSentMessagesBeforeHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
}

data class IcmfMessageAtHeight(
        val height: Long,
        val body: Gtv
)
