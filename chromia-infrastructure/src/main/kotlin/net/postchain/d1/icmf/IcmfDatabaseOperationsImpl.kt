package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.using
import org.jooq.impl.SQLDataType

class IcmfDatabaseOperationsImpl : IcmfDatabaseOperations {

    companion object {
        const val PRIMARY_KEY_PREFIX: String = "PK_"
        const val FOREIGN_KEY_SUFFIX: String = "_FK"
        const val INDEX_PREFIX: String = "IDX_"

        const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell

        const val TABLE_NAME_SENT_ICMF_MESSAGE = "${PREFIX}.sent_icmf_message"

        val COLUMN_CLUSTER: Field<String> = field("cluster", SQLDataType.CLOB.nullable(false))
        val COLUMN_SENDER: Field<ByteArray> = field("sender", SQLDataType.BLOB.nullable(false))
        val COLUMN_TOPIC: Field<String> = field("topic", SQLDataType.CLOB.nullable(false))
        val COLUMN_HEIGHT: Field<Long> = field("height", SQLDataType.BIGINT.nullable(false))
        val COLUMN_SERIAL: Field<Long> = field("serial", SQLDataType.BIGINT.nullable(false).identity(true))
        val COLUMN_ANCHOR_HEIGHT: Field<Long> = field("anchor_height", SQLDataType.BIGINT.nullable(false))
        val COLUMN_MESSAGE_HASH: Field<ByteArray> = field("message_hash", SQLDataType.BLOB.nullable(false))
        val COLUMN_ID: Field<Long> = field("id", SQLDataType.BIGINT.nullable(false).identity(true))
        val COLUMN_TRANSACTION: Field<Long> = field("transaction", SQLDataType.BIGINT.nullable(false))
        val COLUMN_BODY: Field<ByteArray> = field("body", SQLDataType.BLOB.nullable(false))
        val COLUMN_RECEIVER: Field<ByteArray> = field("receiver", SQLDataType.BLOB.nullable(false))
        val COLUMN_SKIP_TO_HEIGHT: Field<Long> = field("skip_to_height", SQLDataType.BIGINT.nullable(false))
        val COLUMN_SENDER_NULLABLE: Field<ByteArray> = field("sender", SQLDataType.BLOB.nullable(true))
        val COLUMN_MERKLE_HASH_VERSION: Field<Long> = field("merkle_hash_version", SQLDataType.BIGINT.defaultValue(1).nullable(false))
    }

    private fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")
    private fun DatabaseAccess.tableMessageHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.message_height")
    private fun DatabaseAccess.tableSpilledMessage(ctx: EContext) = tableName(ctx, "${PREFIX}.spilled_message")
    private fun DatabaseAccess.tableSentIcmfMessage(ctx: EContext) = tableName(ctx, TABLE_NAME_SENT_ICMF_MESSAGE)
    private fun DatabaseAccess.tableDappProvidedReceiverTopic(ctx: EContext) = tableName(ctx, "${PREFIX}.dapp_provided_receiver_topic")

    override fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = createJooq(ctx)

            val anchorTable = table(tableAnchorHeight(ctx))
            jooq.createTableIfNotExists(anchorTable)
                    .column(COLUMN_CLUSTER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_HEIGHT)
                    .constraint(constraint("${PRIMARY_KEY_PREFIX}${anchorTable}").primaryKey(COLUMN_CLUSTER.name, COLUMN_TOPIC.name))
                    .execute()

            val messageTable = table(tableMessageHeight(ctx))
            jooq.createTableIfNotExists(messageTable)
                    .column(COLUMN_SENDER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_HEIGHT)
                    .constraint(constraint("${PRIMARY_KEY_PREFIX}${messageTable}").primaryKey(COLUMN_SENDER.name, COLUMN_TOPIC.name))
                    .execute()

            val spilledMessageTable = table(tableSpilledMessage(ctx))
            jooq.createTableIfNotExists(spilledMessageTable)
                    .column(COLUMN_SERIAL)
                    .column(COLUMN_CLUSTER)
                    .column(COLUMN_ANCHOR_HEIGHT)
                    .column(COLUMN_SENDER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_MESSAGE_HASH)
                    .constraint(constraint("${PRIMARY_KEY_PREFIX}${spilledMessageTable}").primaryKey(COLUMN_SERIAL.name))
                    .execute()

            jooq.alterTable(spilledMessageTable)
                    .addColumnIfNotExists(COLUMN_MERKLE_HASH_VERSION)
                    .execute()

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

            val dappProvidedReceiverTopicTable = table(tableDappProvidedReceiverTopic(ctx))
            val dappProvidedReceiverTopicConstraint = constraint("${PRIMARY_KEY_PREFIX}${dappProvidedReceiverTopicTable}")
            jooq.createTableIfNotExists(dappProvidedReceiverTopicTable)
                    .column(COLUMN_CLUSTER)
                    .column(COLUMN_RECEIVER)
                    .column(COLUMN_TOPIC)
                    .column(COLUMN_SENDER_NULLABLE)
                    .column(COLUMN_SKIP_TO_HEIGHT)
                    .constraint(dappProvidedReceiverTopicConstraint
                            .primaryKey(COLUMN_CLUSTER.name, COLUMN_RECEIVER.name, COLUMN_TOPIC.name))
                    .execute()

            jooq.alterTable(dappProvidedReceiverTopicTable)
                    .dropConstraintIfExists(dappProvidedReceiverTopicConstraint)
                    .execute()

            jooq.alterTable(dappProvidedReceiverTopicTable)
                    .dropColumnIfExists(COLUMN_CLUSTER)
                    .execute()

            jooq.alterTable(dappProvidedReceiverTopicTable)
                    .dropColumnIfExists(COLUMN_RECEIVER)
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
                createJooq(ctx).select(COLUMN_MESSAGE_HASH, COLUMN_SERIAL, COLUMN_CLUSTER, COLUMN_ANCHOR_HEIGHT, COLUMN_MERKLE_HASH_VERSION)
                        .from(tableSpilledMessage(ctx))
                        .where(COLUMN_SENDER.eq(sender.data))
                        .and(COLUMN_TOPIC.eq(topic))
                        .orderBy(COLUMN_SERIAL)
                        .limit(1)
                        .fetchOne()
            }?.map { SpilledMessage(it[COLUMN_SERIAL], it[COLUMN_MESSAGE_HASH], it[COLUMN_CLUSTER], it[COLUMN_ANCHOR_HEIGHT], it[COLUMN_MERKLE_HASH_VERSION]) }

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

    override fun saveSpilledMessage(ctx: EContext, cluster: String, anchorHeight: Long, sender: BlockchainRid, topic: String, hash: ByteArray, merkleHashVersion: Long) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).insertInto(table(tableSpilledMessage(ctx)))
                    .set(COLUMN_SENDER, sender.data)
                    .set(COLUMN_CLUSTER, cluster)
                    .set(COLUMN_ANCHOR_HEIGHT, anchorHeight)
                    .set(COLUMN_TOPIC, topic)
                    .set(COLUMN_MESSAGE_HASH, hash)
                    .set(COLUMN_MERKLE_HASH_VERSION, merkleHashVersion)
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
        createJooq(ctx).select(max(COLUMN_HEIGHT))
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

    override fun deleteDappProvidedReceiverTopics(ctx: EContext) {
        DatabaseAccess.of(ctx).run {
            createJooq(ctx).deleteFrom(table(tableDappProvidedReceiverTopic(ctx)))
                    .execute()
        }
    }

    override fun saveDappProvidedReceiverTopics(ctx: EContext, topics: List<IcmfReceiverEventTopic>) {
        DatabaseAccess.of(ctx).run {
            topics.forEach {
                createJooq(ctx).insertInto(table(tableDappProvidedReceiverTopic(ctx)))
                        .set(COLUMN_TOPIC, it.topic)
                        .set(COLUMN_SENDER_NULLABLE, it.bcRid)
                        .set(COLUMN_SKIP_TO_HEIGHT, it.skipToHeight)
                        .execute()
            }
        }
    }

    override fun loadDappProvidedReceiverTopics(ctx: EContext): List<IcmfReceiverEventTopic> {
        return DatabaseAccess.of(ctx).run {
            val jooq = createJooq(ctx)
            val table = tableDappProvidedReceiverTopic(ctx)
            jooq.select(COLUMN_TOPIC, COLUMN_SENDER, COLUMN_SKIP_TO_HEIGHT)
                    .from(table)
                    .fetch()
                    .map { IcmfReceiverEventTopic(it[COLUMN_TOPIC], it[COLUMN_SENDER], it[COLUMN_SKIP_TO_HEIGHT]) }
        }
    }

    private fun createJooq(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
