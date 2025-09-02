package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface IcmfSenderDatabaseOperations {
    fun initialize(ctx: EContext)
    fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray)
    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, blockHeight: Long): Long
    fun getSentMessagesAfterHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight>
    fun getSentMessagesAtHeight(ctx: EContext, topic: String, blockHeight: Long): List<Gtv>
    fun getAllTopics(ctx: EContext): List<String>
    fun getSentMessagesAfterId(ctx: EContext, topic: String, id: Long, limit: Int): List<IcmfMessageAtHeightWithId>
    fun getSentMessagesBeforeId(ctx: EContext, topic: String, id: Long, limit: Int): List<IcmfMessageAtHeightWithId>
}

data class IcmfMessageAtHeight(
        val height: Long,
        val body: Gtv
)

data class IcmfMessageAtHeightWithId(
        val id: Long,
        val height: Long,
        val body: Gtv
)
