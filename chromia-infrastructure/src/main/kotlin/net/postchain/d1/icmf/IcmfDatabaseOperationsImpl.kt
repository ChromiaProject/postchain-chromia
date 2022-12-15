package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL.*
import org.jooq.util.postgres.PostgresDataType

class IcmfDatabaseOperationsImpl : IcmfDatabaseOperations {

    companion object {
        const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell

        val COLUMN_CLUSTER: Field<String> = field("cluster", PostgresDataType.TEXT.nullable(false))
        val COLUMN_SENDER: Field<ByteArray> = field("sender", PostgresDataType.BYTEA.nullable(false))
        val COLUMN_TOPIC: Field<String> = field("topic", PostgresDataType.TEXT.nullable(false))
        val COLUMN_HEIGHT: Field<Long> = field("height", PostgresDataType.BIGINT.nullable(false))
        val COLUMN_SERIAL: Field<Long> = field("serial", PostgresDataType.BIGSERIAL.nullable(false))
        val COLUMN_ANCHOR_HEIGHT: Field<Long> = field("anchor_height", PostgresDataType.BIGINT.nullable(false))
        val COLUMN_MESSAGE_HASH: Field<ByteArray> = field("message_hash", PostgresDataType.BYTEA.nullable(false))
    }

    private fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
    private fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")
    private fun DatabaseAccess.tableSpilledMessage(ctx: EContext) = tableName(ctx, "${PREFIX}.spilled_message")

    override fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = createJooq(ctx)

            val anchorTable = table(tableAnchorHeight(ctx))
            jooq.createTableIfNotExists(anchorTable)
                    .column(COLUMN_CLUSTER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_HEIGHT)
                    .constraint(constraint("PK_${anchorTable}").primaryKey("cluster", "topic"))
                    .execute()

            val messageTable = table(tableMessageHeight(ctx))
            jooq.createTableIfNotExists(messageTable)
                    .column(COLUMN_SENDER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_HEIGHT)
                    .constraint(constraint("PK_${messageTable}").primaryKey("sender", "topic"))
                    .execute()

            val spilledMessageTable = table(tableSpilledMessage(ctx))
            jooq.createTableIfNotExists(spilledMessageTable)
                    .column(COLUMN_SERIAL)
                    .column(COLUMN_CLUSTER)
                    .column(COLUMN_ANCHOR_HEIGHT)
                    .column(COLUMN_SENDER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_MESSAGE_HASH)
                    .constraint(constraint("PK_${spilledMessageTable}").primaryKey("serial"))
                    .execute()
        }
    }

    override fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_HEIGHT)
                .from(tableAnchorHeight(ctx))
                .where(COLUMN_CLUSTER.eq(clusterName))
                .and(COLUMN_TOPIC.eq(topic))
                .fetchOne()?.value1()
    } ?: -1

    override fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight> = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_CLUSTER, COLUMN_TOPIC, COLUMN_HEIGHT)
                .from(tableAnchorHeight(ctx))
                .fetch()
    }.map { AnchorHeight(it[COLUMN_CLUSTER], it[COLUMN_TOPIC], it[COLUMN_HEIGHT]) }

    override fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).insertInto(table(tableAnchorHeight(ctx)))
                    .set(COLUMN_CLUSTER, clusterName)
                    .set(COLUMN_TOPIC, topic)
                    .set(COLUMN_HEIGHT, anchorHeight)
                    .onConflict(COLUMN_CLUSTER, COLUMN_TOPIC)
                    .doUpdate()
                    .set(COLUMN_HEIGHT, anchorHeight)
                    .execute()
        }
    }

    override fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender> = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_SENDER, COLUMN_TOPIC, COLUMN_HEIGHT)
                .from(tableMessageHeight(ctx))
                .fetch()
    }.map { MessageHeightForSender(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_HEIGHT]) }

    override fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long = DatabaseAccess.of(ctx).run {
        createJooq(ctx).select(COLUMN_HEIGHT)
                .from(tableMessageHeight(ctx))
                .where(COLUMN_SENDER.eq(sender.data))
                .and(COLUMN_TOPIC.eq(topic))
                .fetchOne()?.value1()
    } ?: -1

    override fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).insertInto(table(tableMessageHeight(ctx)))
                    .set(COLUMN_SENDER, sender.data)
                    .set(COLUMN_TOPIC, topic)
                    .set(COLUMN_HEIGHT, height)
                    .onConflict(COLUMN_SENDER, COLUMN_TOPIC)
                    .doUpdate()
                    .set(COLUMN_HEIGHT, height)
                    .execute()
        }
    }

    override fun loadOldestSpilledMessage(ctx: EContext, sender: BlockchainRid, topic: String): SpilledMessage? =
            DatabaseAccess.of(ctx).run {
                createJooq(ctx).select(COLUMN_MESSAGE_HASH, COLUMN_SERIAL, COLUMN_CLUSTER, COLUMN_ANCHOR_HEIGHT)
                        .from(tableSpilledMessage(ctx))
                        .where(COLUMN_SENDER.eq(sender.data))
                        .and(COLUMN_TOPIC.eq(topic))
                        .orderBy(COLUMN_SERIAL)
                        .limit(1)
                        .fetchOne()
            }.map { SpilledMessage(it[COLUMN_SERIAL], it[COLUMN_MESSAGE_HASH], it[COLUMN_CLUSTER], it[COLUMN_ANCHOR_HEIGHT]) }

    override fun loadSpilledMessageCounts(ctx: EContext, cluster: String, anchorHeight: Long, topic: String): Map<BlockchainRid, Int> =
            DatabaseAccess.of(ctx).run {
                createJooq(ctx).select(COLUMN_SENDER, count(COLUMN_SERIAL))
                        .from(tableSpilledMessage(ctx))
                        .where(COLUMN_CLUSTER.eq(cluster))
                        .and(COLUMN_ANCHOR_HEIGHT.eq(anchorHeight))
                        .and(COLUMN_TOPIC.eq(topic))
                        .groupBy(COLUMN_SENDER)
                        .fetch()
            }.map { BlockchainRid(it[COLUMN_SENDER]) to it[1] as Int }.toMap()

    override fun saveSpilledMessage(ctx: EContext, cluster: String, anchorHeight: Long, sender: BlockchainRid, topic: String, hash: ByteArray) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).insertInto(table(tableSpilledMessage(ctx)))
                    .set(COLUMN_SENDER, sender.data)
                    .set(COLUMN_CLUSTER, cluster)
                    .set(COLUMN_ANCHOR_HEIGHT, anchorHeight)
                    .set(COLUMN_TOPIC, topic)
                    .set(COLUMN_MESSAGE_HASH, hash)
                    .execute()
        }
    }

    override fun imprecateSpilledMessage(ctx: EContext, serial: Long) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).deleteFrom(table(tableSpilledMessage(ctx)))
                    .where(COLUMN_SERIAL.eq(serial))
                    .execute()
        }
    }

    private fun createJooq(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
