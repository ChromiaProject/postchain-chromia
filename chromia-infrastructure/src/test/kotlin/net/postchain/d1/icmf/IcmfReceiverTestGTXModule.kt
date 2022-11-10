package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageOp
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_BODY
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_HEIGHT
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_SENDER
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_TOPIC
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.testMessageTable
import net.postchain.gtv.GtvEncoder
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.table
import org.jooq.util.postgres.PostgresDataType

class IcmfReceiverTestGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(MessageOp.OP_NAME to ::IcmfMessageOp),
        mapOf()
) {

    companion object {
        const val testMessageTable = "test_messages"

        val COLUMN_ID: Field<Long> = DSL.field("id", PostgresDataType.BIGSERIAL.nullable(false))
        val COLUMN_SENDER: Field<ByteArray> = DSL.field("sender", PostgresDataType.BYTEA.nullable(false))
        val COLUMN_TOPIC: Field<String> = DSL.field("topic", PostgresDataType.TEXT.nullable(false))
        val COLUMN_BODY: Field<ByteArray> = DSL.field("body", PostgresDataType.BYTEA.nullable(false))
        val COLUMN_HEIGHT: Field<Long> = DSL.field("height", PostgresDataType.BIGINT.nullable(false))
    }

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
            jooq.createTableIfNotExists(table(tableName(ctx, testMessageTable)))
                    .column(COLUMN_ID)
                    .column(COLUMN_SENDER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_BODY)
                    .column(COLUMN_HEIGHT)
                    .constraint(constraint("PK").primaryKey(COLUMN_ID))
                    .execute()
        }
    }

}

class IcmfMessageOp(@Suppress("UNUSED_PARAMETER") u: Unit, private val opdata: ExtOpData) : GTXOperation(opdata) {
    override fun isCorrect(): Boolean {
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        DatabaseAccess.of(ctx).apply {
            val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
            jooq.insertInto(table(tableName(ctx, testMessageTable)))
                    .set(COLUMN_SENDER, opdata.args[0].asByteArray())
                    .set(COLUMN_TOPIC, opdata.args[1].asString())
                    .set(COLUMN_BODY, GtvEncoder.encodeGtv(opdata.args[2]))
                    .set(COLUMN_HEIGHT, ctx.height)
                    .execute()
        }
        return true
    }
}
