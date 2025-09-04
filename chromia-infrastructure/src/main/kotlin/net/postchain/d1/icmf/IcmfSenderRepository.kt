package net.postchain.d1.icmf

import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv

class IcmfSenderRepository(
        private val icmfSenderGTXModuleContext: IcmfSenderGTXModuleContext
) {

    fun persistMessage(ctxt: TxEContext, topic: String, body: Gtv) {
        val id = icmfSenderGTXModuleContext.dbOperations
                .saveSentMessage(ctxt, ctxt.txIID, topic, ctxt.height, GtvEncoder.encodeGtv(body))

        icmfSenderGTXModuleContext.snapshotContext?.emitDatum(
                ctxt,
                messageIdToDatumId(id),
                IcmfSentMessageDatum(ctxt.txIID, ctxt.height, topic, body).toGtv(),
                true
        )
    }

    fun persistDatums(ctx: EContext, datums: List<SnapshotDatum>) {
        val messages = datums.map {
            val datum = IcmfSentMessageDatum.fromGtv(it.data)
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
                IcmfSentMessageDatum(
                        transactionId = it.transactionId,
                        height = it.height,
                        topic = it.topic,
                        body = it.body
                ).toGtv()
            }

    fun getPermanentDatumIdMax(ctx: EContext): Long? = icmfSenderGTXModuleContext.dbOperations.getMaxMessageId(ctx)?.let { messageIdToDatumId(it) }

    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, height: Long) = icmfSenderGTXModuleContext.dbOperations
            .getPreviousSentMessageBlockHeight(ctx, topic, height)

    private fun datumIdToMessageId(datumId: Long) = datumId + 1
    private fun messageIdToDatumId(messageId: Long) = messageId - 1
}

data class IcmfSentMessageDatum(
        val transactionId: Long,
        val height: Long,
        val topic: String,
        val body: Gtv
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfSentMessageDatum {
            val data = gtv as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
            if (data.getSize() != 4) throw ProgrammerMistake("Invalid data. Must contain 4 elements.")

            return IcmfSentMessageDatum(
                    data[0].asInteger(),
                    data[1].asInteger(),
                    data[2].asString(),
                    data[3]
            )
        }
    }

    fun toGtv(): Gtv = gtv(listOf(
            gtv(transactionId),
            gtv(height),
            gtv(topic),
            body
    ))
}
