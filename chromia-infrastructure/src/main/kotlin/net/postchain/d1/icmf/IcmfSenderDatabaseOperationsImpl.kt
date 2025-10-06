package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
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

        val COLUMN_DATUM_ID = field("datum_id", SQLDataType.BIGINT.nullable(true)) // Nullability will be removed after initial migration

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
                                    .references(tableName(ctx, "transactions").replace("\"", ""), "tx_iid"),
                    )
                    .execute()
            jooq.createIndexIfNotExists("${INDEX_PREFIX}${simTableName}_0")
                    .on(simTableName, COLUMN_TOPIC.name, COLUMN_HEIGHT.name)
                    .execute()

            jooq.alterTable(table(tableSentIcmfMessage(ctx), TABLE_NAME_SENT_ICMF_MESSAGE))
                    .addColumnIfNotExists(COLUMN_DATUM_ID)
                    .execute()

            // Do migration if necessary
            val datumIdMigrated = jooq.fetchExists(
                    jooq.selectFrom("INFORMATION_SCHEMA.COLUMNS")
                            .where(field("table_name").eq(tableSentIcmfMessage(ctx).replace("\"", "")))
                            .and(field("column_name").eq(COLUMN_DATUM_ID.name))
                            .and(field("is_nullable").eq("NO"))
            )
            if (!datumIdMigrated) {
                jooq.execute("""
                    UPDATE ${tableSentIcmfMessage(ctx)} 
                    SET datum_id = subquery.datum_id_seq 
                    FROM (
                        SELECT id, (ROW_NUMBER() OVER (ORDER BY id) - 1) AS datum_id_seq 
                        FROM ${tableSentIcmfMessage(ctx)}
                    ) AS subquery 
                    WHERE ${tableSentIcmfMessage(ctx)}.id = subquery.id
                """)

                jooq.alterTable(table(tableSentIcmfMessage(ctx), TABLE_NAME_SENT_ICMF_MESSAGE))
                        .add(constraint("${simTableName}_${COLUMN_DATUM_ID.name}_unique").unique(COLUMN_DATUM_ID.name))
                        .execute()

                jooq.alterTable(table(tableSentIcmfMessage(ctx), TABLE_NAME_SENT_ICMF_MESSAGE))
                        .alterColumn(COLUMN_DATUM_ID)
                        .setNotNull()
                        .execute()
            }
        }
    }

    override fun saveSentMessage(ctx: EContext, transactionIid: Long, topic: String, height: Long, body: ByteArray): Long = DatabaseAccess.of(ctx).run {
        createJooq(ctx).insertInto(table(tableSentIcmfMessage(ctx)))
                .set(COLUMN_DATUM_ID, DSL.coalesce(
                        DSL.select(DSL.max(COLUMN_DATUM_ID).plus(1))
                                .from(table(tableSentIcmfMessage(ctx)))
                                .asField(),
                        0L
                ))
                .set(COLUMN_TRANSACTION, transactionIid)
                .set(COLUMN_TOPIC, topic)
                .set(COLUMN_HEIGHT, height)
                .set(COLUMN_BODY, body)
                .returning(COLUMN_DATUM_ID)
                .fetchOne()!![COLUMN_DATUM_ID]
    }

    override fun saveSentMessagesWithDatumId(ctx: EContext, messages: List<SentIcmfMessageData>) {
        if (messages.isEmpty()) return

        DatabaseAccess.of(ctx).run {
            val jooq = createJooq(ctx)

            val valuesRows = messages.map { message ->
                DSL.row(
                        message.datumId,
                        DSL.value(message.transactionRid),
                        message.topic,
                        message.height,
                        GtvEncoder.encodeGtv(message.body)
                )
            }

            val valuesTable = DSL.values(*valuesRows.toTypedArray())
                    .`as`("message_data", "datum_id", "tx_rid", "topic", "height", "body")

            // Insert with join to get tx_iid from tx_rid
            jooq.insertInto(table(tableSentIcmfMessage(ctx)))
                    .columns(COLUMN_DATUM_ID, COLUMN_TRANSACTION, COLUMN_TOPIC, COLUMN_HEIGHT, COLUMN_BODY)
                    .select(
                            DSL.select(
                                    field("message_data.datum_id", SQLDataType.BIGINT),
                                    field("tx_iid", SQLDataType.BIGINT),
                                    field("message_data.topic", SQLDataType.CLOB),
                                    field("message_data.height", SQLDataType.BIGINT),
                                    field("message_data.body", SQLDataType.BLOB)
                            )
                                    .from(valuesTable)
                                    .join(table(tableName(ctx, "transactions")).asTable("t"))
                                    .on(field("t.tx_rid", SQLDataType.BLOB).eq(field("message_data.tx_rid", SQLDataType.BLOB)))
                    )
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

    override fun getSentMessagesBeforeHeight(ctx: EContext, topic: String, blockHeight: Long, limit: Int): List<IcmfMessageAtHeight> {
        val sentMessages = DatabaseAccess.of(ctx).run {
            createJooq(ctx).select(COLUMN_ID, COLUMN_HEIGHT, COLUMN_BODY)
                    .from(tableSentIcmfMessage(ctx))
                    .where(COLUMN_TOPIC.eq(topic))
                    .and(COLUMN_HEIGHT.lt(blockHeight))
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

    override fun getMaxDatumId(ctx: EContext): Long? = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(DSL.max(COLUMN_DATUM_ID))
                .from(tableSentIcmfMessage(ctx))
                .fetchOne()?.value1()
    }

    override fun streamSentMessagesFromDatumId(ctx: EContext, from: Long, rowHandler: (SentIcmfMessageData) -> Boolean) {
        DatabaseAccess.of(ctx).run {
            val jooq = createJooq(ctx)
            val cursor = jooq.select(COLUMN_DATUM_ID, field("tx_rid", SQLDataType.BLOB), COLUMN_HEIGHT, COLUMN_TOPIC, COLUMN_BODY)
                    .from(tableSentIcmfMessage(ctx))
                    .join(tableName(ctx, "transactions"))
                    .on(COLUMN_TRANSACTION.eq(DSL.field("tx_iid", Long::class.java)))
                    .where(COLUMN_DATUM_ID.ge(from))
                    .orderBy(COLUMN_DATUM_ID)
                    .fetchSize(1)
                    .fetchLazy()
            cursor.use { c ->
                for (rec in c) {
                    val row = SentIcmfMessageData(
                            datumId = rec[COLUMN_DATUM_ID],
                            transactionRid = rec[field("tx_rid", SQLDataType.BLOB)],
                            height = rec[COLUMN_HEIGHT],
                            topic = rec[COLUMN_TOPIC],
                            body = GtvDecoder.decodeGtv(rec[COLUMN_BODY])
                    )
                    if (!rowHandler(row)) break
                }
            }
        }
    }

    private fun createJooq(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
