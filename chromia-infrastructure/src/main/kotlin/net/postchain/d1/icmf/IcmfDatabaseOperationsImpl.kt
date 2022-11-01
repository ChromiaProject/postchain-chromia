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
    }

    private fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
    private fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")

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

    private fun createJooq(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
