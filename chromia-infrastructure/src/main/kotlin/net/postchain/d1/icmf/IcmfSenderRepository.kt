package net.postchain.d1.icmf

import net.postchain.base.BaseTxEContext
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
        val datumId = icmfSenderGTXModuleContext.dbOperations
                .saveSentMessage(ctxt, ctxt.txIID, topic, ctxt.height, GtvEncoder.encodeGtv(body))

        val txRid = (ctxt as BaseTxEContext).tx.getRID() // TODO CAN WE PUT THIS IN INTERFACE?
        icmfSenderGTXModuleContext.snapshotContext?.emitDatum(
                ctxt,
                datumId,
                IcmfSentMessageDatum(txRid, ctxt.height, topic, body).toGtv(),
                true
        )
    }

    fun persistDatums(ctx: EContext, datums: List<SnapshotDatum>) {
        val messages = datums.map {
            val datum = IcmfSentMessageDatum.fromGtv(it.data)
            SentIcmfMessageData(
                    datumId = it.id,
                    transactionRid = datum.transactionRid,
                    height = datum.height,
                    topic = datum.topic,
                    body = datum.body
            )
        }

        icmfSenderGTXModuleContext.dbOperations.saveSentMessagesWithDatumId(ctx, messages)
    }

    fun getPermanentDatumIdMax(ctx: EContext): Long? = icmfSenderGTXModuleContext.dbOperations.getMaxDatumId(ctx)

    fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, height: Long) = icmfSenderGTXModuleContext.dbOperations
            .getPreviousSentMessageBlockHeight(ctx, topic, height)


    fun streamPermanentDatums(ctx: EContext, datumIdFrom: Long, datumHandler: (datum: SnapshotDatum?) -> Boolean) {
        var continueStreaming = true
        icmfSenderGTXModuleContext.dbOperations.streamSentMessagesFromDatumId(ctx, datumIdFrom) { row ->
            if (!continueStreaming) return@streamSentMessagesFromDatumId false
            val datumGtv = IcmfSentMessageDatum(
                    transactionRid = row.transactionRid,
                    height = row.height,
                    topic = row.topic,
                    body = row.body
            ).toGtv()
            val datum = SnapshotDatum(row.datumId, datumGtv, true)
            continueStreaming = datumHandler(datum)
            continueStreaming
        }
        // Signal end of stream
        if (continueStreaming) {
            datumHandler(null)
        }
    }
}

data class IcmfSentMessageDatum(
        val transactionRid: ByteArray,
        val height: Long,
        val topic: String,
        val body: Gtv
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfSentMessageDatum {
            val data = gtv as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
            if (data.getSize() != 4) throw ProgrammerMistake("Invalid data. Must contain 4 elements.")

            return IcmfSentMessageDatum(
                    data[0].asByteArray(),
                    data[1].asInteger(),
                    data[2].asString(),
                    data[3]
            )
        }
    }

    fun toGtv(): Gtv = gtv(listOf(
            gtv(transactionRid),
            gtv(height),
            gtv(topic),
            body
    ))
}
