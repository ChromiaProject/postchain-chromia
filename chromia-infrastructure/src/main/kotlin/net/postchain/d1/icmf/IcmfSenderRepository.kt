package net.postchain.d1.icmf

import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject

class IcmfSenderRepository(
        private val icmfSenderGTXModuleContext: IcmfSenderGTXModuleContext
) {

    fun persistMessage(ctxt: TxEContext, topic: String, body: Gtv) {
        val id = icmfSenderGTXModuleContext.dbOperations
                .saveSentMessage(ctxt, ctxt.txIID, topic, ctxt.height, GtvEncoder.encodeGtv(body))

        icmfSenderGTXModuleContext.snapshotContext?.emitDatum(
                ctxt,
                messageIdToDatumId(id),
                GtvObjectMapper.toGtvDictionary(
                        IcmfSentMessageDatum(ctxt.txIID, ctxt.height, topic, body)
                ),
                true
        )
    }

    fun persistDatums(ctx: EContext, datums: List<SnapshotDatum>) {
        val messages = datums.map {
            val datum = it.data.toObject<IcmfSentMessageDatum>()
            SentIcmfMessageData(
                    id = datumIdToMessageId(it.id),
                    transactionId = datum.transactionId,
                    height = datum.height,
                    topic = datum.topic,
                    body = datum.body
            )
        }

        icmfSenderGTXModuleContext.dbOperations.saveSentMessagesWithId(ctx, messages)
    }

    fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv? = icmfSenderGTXModuleContext.dbOperations
            .getSentMessageById(ctx, datumIdToMessageId(datumId))?.let {
                GtvObjectMapper.toGtvDictionary(IcmfSentMessageDatum(
                        transactionId = it.transactionId,
                        height = it.height,
                        topic = it.topic,
                        body = it.body
                ))
            }

    fun getPermanentDatumIdMax(ctx: EContext): Long? = icmfSenderGTXModuleContext.dbOperations.getMaxMessageId(ctx)?.let { messageIdToDatumId(it) }

    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, height: Long) = icmfSenderGTXModuleContext.dbOperations
            .getPreviousSentMessageBlockHeight(ctx, topic, height)

    private fun datumIdToMessageId(datumId: Long) = datumId + 1
    private fun messageIdToDatumId(messageId: Long) = messageId - 1
}

data class IcmfSentMessageDatum(
        @Name("transaction_id")
        val transactionId: Long,
        @Name("height")
        val height: Long,
        @Name("topic")
        val topic: String,
        @Name("body")
        val body: Gtv
)
