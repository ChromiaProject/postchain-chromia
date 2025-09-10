package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface IcmfSenderDatabaseOperations {
    fun initialize(ctx: EContext)
    fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray): Long
    fun saveSentMessagesWithDatumId(ctx: EContext, messages: List<SentIcmfMessageData>)
    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, blockHeight: Long): Long
    fun getSentMessagesAfterHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
    fun getSentMessagesAtHeight(ctx: EContext, topic: String, blockHeight: Long): List<Gtv>
    fun getAllTopics(ctx: EContext): List<String>
    fun getSentMessagesBeforeHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
    fun getMaxDatumId(ctx: EContext): Long?
    fun streamSentMessagesFromDatumId(ctx: EContext, from: Long, rowHandler: (SentIcmfMessageData) -> Boolean)
}

data class IcmfMessageAtHeight(
        val height: Long,
        val body: Gtv
)

data class SentIcmfMessageData(
        val datumId: Long,
        val transactionRid: ByteArray,
        val height: Long,
        val topic: String,
        val body: Gtv
)
