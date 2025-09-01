package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.using
import org.jooq.impl.SQLDataType

class IcmfSenderDatabaseOperationsImpl : IcmfSenderDatabaseOperations {

    companion object {
        const val PRIMARY_KEY_PREFIX: String = "PK_"
        const val FOREIGN_KEY_SUFFIX: String = "_FK"
        const val INDEX_PREFIX: String = "IDX_"

        const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell

        const val TABLE_NAME_SENT_ICMF_MESSAGE = "${PREFIX}.sent_icmf_message"

        val COLUMN_ID: Field<Long> = field("id", SQLDataType.BIGINT.nullable(false).identity(true))
        val COLUMN_TRANSACTION: Field<Long> = field("transaction", SQLDataType.BIGINT.nullable(false))
        val COLUMN_BODY: Field<ByteArray> = field("body", SQLDataType.BLOB.nullable(false))
        val COLUMN_TOPIC: Field<String> = field("topic", SQLDataType.CLOB.nullable(false))
        val COLUMN_HEIGHT: Field<Long> = field("height", SQLDataType.BIGINT.nullable(false))
    }

    private fun DatabaseAccess.tableSentIcmfMessage(ctx: EContext) = tableName(ctx, TABLE_NAME_SENT_ICMF_MESSAGE)

    override fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = createJooq(ctx)

            val simTableName = tableName(ctx, TABLE_NAME_SENT_ICMF_MESSAGE).replace("\"", "")
            jooq.createTableIfNotExists(table(tableSentIcmfMessage(ctx), TABLE_NAME_SENT_ICMF_MESSAGE))
                    .column(COLUMN_ID)
                    .column(COLUMN_TRANSACTION)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_HEIGHT)
                    .column(COLUMN_BODY)
                    .constraints(
                            constraint("${PRIMARY_KEY_PREFIX}$simTableName").primaryKey(COLUMN_ID.name),
                            constraint("${simTableName}_${COLUMN_TRANSACTION.name}${FOREIGN_KEY_SUFFIX}")
                                    .foreignKey(COLUMN_TRANSACTION.name)
                                    .references(tableName(ctx, "transactions").replace("\"", ""), "tx_iid")
                    )
                    .execute()
            jooq.createIndexIfNotExists("${INDEX_PREFIX}${simTableName}_0")
                    .on(simTableName, COLUMN_TOPIC.name, COLUMN_HEIGHT.name)
                    .execute()
        }
    }

    override fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).insertInto(table(tableSentIcmfMessage(ctx)))
                    .set(COLUMN_TRANSACTION, transactionIid)
                    .set(COLUMN_TOPIC, topic)
                    .set(COLUMN_HEIGHT, height)
                    .set(COLUMN_BODY, body)
                    .execute()
        }
    }

    override fun getPreviousSentMessageBlockHeight(ctx: EContext, topic: String, blockHeight: Long): Long = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(DSL.max(COLUMN_HEIGHT))
                .from(tableSentIcmfMessage(ctx))
                .where(COLUMN_TOPIC.eq(topic))
                .and(COLUMN_HEIGHT.lessThan(blockHeight))
                .fetchOne()?.value1()
    } ?: -1

    override fun getSentMessagesAfterHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight> {
        val sentMessages = DatabaseAccess.of(ctx).run {
            createJooq(ctx).select(COLUMN_ID, COLUMN_HEIGHT, COLUMN_BODY)
                    .from(tableSentIcmfMessage(ctx))
                    .where(COLUMN_TOPIC.eq(topic))
                    .and(COLUMN_HEIGHT.gt(blockHeight))
                    .orderBy(COLUMN_ID)
                    .limit(limit)
                    .fetch()
        }.map {
            it[COLUMN_ID] to IcmfMessageAtHeight(it[COLUMN_HEIGHT], GtvDecoder.decodeGtv(it[COLUMN_BODY]))
        }
        if (sentMessages.size == limit) {
            sentMessages.addAll(
                    DatabaseAccess.of(ctx).run {
                        createJooq(ctx).select(COLUMN_ID, COLUMN_HEIGHT, COLUMN_BODY)
                                .from(tableSentIcmfMessage(ctx))
                                .where(COLUMN_TOPIC.eq(topic))
                                .and(COLUMN_HEIGHT.eq(sentMessages.last().second.height))
                                .and(COLUMN_ID.gt(sentMessages.last().first))
                                .orderBy(COLUMN_ID)
                                .fetch()
                    }.map {
                        it[COLUMN_ID] to IcmfMessageAtHeight(it[COLUMN_HEIGHT], GtvDecoder.decodeGtv(it[COLUMN_BODY]))
                    }
            )
        }
        return sentMessages.map { it.second }
    }

    override fun getSentMessagesAtHeight(ctx: EContext, topic: String, blockHeight: Long): List<Gtv> = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_BODY)
                .from(tableSentIcmfMessage(ctx))
                .where(COLUMN_TOPIC.eq(topic))
                .and(COLUMN_HEIGHT.eq(blockHeight))
                .orderBy(COLUMN_ID)
                .fetch()
    }.map {
        GtvDecoder.decodeGtv(it[COLUMN_BODY])
    }

    override fun getAllTopics(ctx: EContext): List<String> {
        return DatabaseAccess.of(ctx).run {
            createJooq(ctx).selectDistinct(COLUMN_TOPIC)
                    .from(tableSentIcmfMessage(ctx))
                    .orderBy(COLUMN_TOPIC)
                    .fetch()
        }.map {
            it[COLUMN_TOPIC]
        }
    }

    override fun getSentMessagesAfterId(ctx: EContext, topic: String, id: Long, limit: Int): List<IcmfMessageAtHeightWithId> = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_ID, COLUMN_HEIGHT, COLUMN_BODY)
                .from(tableSentIcmfMessage(ctx))
                .where(COLUMN_TOPIC.eq(topic))
                .and(COLUMN_ID.gt(id))
                .orderBy(COLUMN_ID)
                .limit(limit)
                .fetch()
    }.map<IcmfMessageAtHeightWithId> {
        IcmfMessageAtHeightWithId(id = it[COLUMN_ID], height = it[COLUMN_HEIGHT], body = GtvDecoder.decodeGtv(it[COLUMN_BODY]))
    }

    override fun getSentMessagesBeforeId(ctx: EContext, topic: String, id: Long, limit: Int): List<IcmfMessageAtHeightWithId> = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_ID, COLUMN_HEIGHT, COLUMN_BODY)
                .from(tableSentIcmfMessage(ctx))
                .where(COLUMN_TOPIC.eq(topic))
                .and(COLUMN_ID.lt(id))
                .orderBy(COLUMN_ID.desc())
                .limit(limit)
                .fetch()
    }.map<IcmfMessageAtHeightWithId> {
        IcmfMessageAtHeightWithId(id = it[COLUMN_ID], height = it[COLUMN_HEIGHT], body = GtvDecoder.decodeGtv(it[COLUMN_BODY]))
    }

    private fun createJooq(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
