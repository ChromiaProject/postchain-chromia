package net.postchain.hybridcompute

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.using
import org.jooq.impl.SQLDataType
import java.sql.Timestamp
import java.time.Instant
import kotlin.time.Duration

class HybridComputeDatabaseOperationsImpl : HybridComputeDatabaseOperations {

    companion object {
        const val PRIMARY_KEY_PREFIX: String = "PK_"

        const val PREFIX: String = "sys.x.hc" // This name should not clash with Rell

        val TABLE_REQUESTS = table(tableName("${PREFIX}.requests")) // global table, not per blockchain
        val COLUMN_CONTAINER: Field<String> = field("container", SQLDataType.CLOB.nullable(false))
        val COLUMN_TYPE: Field<String> = field("type", SQLDataType.CLOB.nullable(false))
        val COLUMN_PERIOD_START: Field<Timestamp> = field("period_start", SQLDataType.TIMESTAMP.nullable(false))
        val COLUMN_REQUESTS: Field<Long> = field("requests", SQLDataType.BIGINT.nullable(false))

        fun tableName(name: String) = "\"$name\""
    }

    override fun initialize(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = dslContext(ctx)

            jooq.createTableIfNotExists(TABLE_REQUESTS)
                    .column(COLUMN_CONTAINER)
                    .column(COLUMN_TYPE)
                    .column(COLUMN_PERIOD_START)
                    .column(COLUMN_REQUESTS)
                    .constraint(constraint("${PRIMARY_KEY_PREFIX}${TABLE_REQUESTS}")
                            .primaryKey(COLUMN_CONTAINER, COLUMN_TYPE, COLUMN_PERIOD_START))
                    .execute()
        }
    }

    override fun fetchRequests(ctx: EContext, container: String, type: String, now: Instant, periodLength: Duration): Long =
            DatabaseAccess.of(ctx).run {
                dslContext(ctx).select(COLUMN_REQUESTS)
                        .from(TABLE_REQUESTS)
                        .where(COLUMN_CONTAINER.eq(container))
                        .and(COLUMN_TYPE.eq(type))
                        .and(COLUMN_PERIOD_START.gt(Timestamp(now.toEpochMilli() - periodLength.inWholeMilliseconds)))
                        .orderBy(COLUMN_PERIOD_START.desc())
                        .limit(1)
                        .fetchOne()?.value1() ?: 0
            }

    override fun incrementRequests(ctx: EContext, container: String, type: String,
                                   containerCreationTime: Instant?, now: Instant, periodLength: Duration) {
        DatabaseAccess.of(ctx).run {
            val existing = dslContext(ctx).select(COLUMN_PERIOD_START, COLUMN_REQUESTS)
                    .from(TABLE_REQUESTS)
                    .where(COLUMN_CONTAINER.eq(container))
                    .and(COLUMN_TYPE.eq(type))
                    .orderBy(COLUMN_PERIOD_START.desc())
                    .limit(1)
                    .fetchOne()
            if (existing != null) {
                val periodStart = existing[COLUMN_PERIOD_START]
                val requests = existing[COLUMN_REQUESTS]
                if (periodStart.time > now.toEpochMilli() - periodLength.inWholeMilliseconds) {
                    dslContext(ctx).update(TABLE_REQUESTS)
                            .set(COLUMN_REQUESTS, requests + 1)
                            .where(COLUMN_CONTAINER.eq(container))
                            .and(COLUMN_TYPE.eq(type))
                            .and(COLUMN_PERIOD_START.eq(periodStart))
                            .execute()
                } else {
                    dslContext(ctx).insertInto(TABLE_REQUESTS)
                            .set(COLUMN_CONTAINER, container)
                            .set(COLUMN_TYPE, type)
                            .set(COLUMN_PERIOD_START, Timestamp(
                                    ((now.toEpochMilli() - periodStart.time) / periodLength.inWholeMilliseconds) * periodLength.inWholeMilliseconds + periodStart.time
                            ))
                            .set(COLUMN_REQUESTS, 1)
                            .execute()
                }
            } else {
                dslContext(ctx).insertInto(TABLE_REQUESTS)
                        .set(COLUMN_CONTAINER, container)
                        .set(COLUMN_TYPE, type)
                        .set(COLUMN_PERIOD_START, Timestamp((containerCreationTime ?: now).toEpochMilli()))
                        .set(COLUMN_REQUESTS, 1)
                        .execute()
            }
        }
    }

    internal fun dslContext(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
