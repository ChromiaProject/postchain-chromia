package net.postchain.hybridcompute.it

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.data.testDbConfig
import net.postchain.base.withWriteConnection
import net.postchain.config.app.AppConfig
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl.Companion.COLUMN_CONTAINER
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl.Companion.COLUMN_PERIOD_START
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl.Companion.COLUMN_REQUESTS
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl.Companion.COLUMN_TYPE
import net.postchain.hybridcompute.HybridComputeDatabaseOperationsImpl.Companion.TABLE_REQUESTS
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class HybridComputeDatabaseOperationsImplIT {

    val appConfig: AppConfig = testDbConfig("hybrid_compute_db_it")

    @Test
    fun `should increment existing requests when record matches within period, and create new record when period is exceeded`() {
        val operations = HybridComputeDatabaseOperationsImpl()
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->
                    withWriteConnection(storage, 100) { ctx ->
                        operations.initialize(ctx)

                        val container = "testContainer"
                        val type = "testType"
                        val now = Instant.now()
                        val containerCreationTime = now.minusSeconds(2 * 24 * 60 * 60)
                        val periodLength: Duration = 7.days

                        // Ensure there is no matching record
                        assertThat(operations.dslContext(ctx).fetchCount(TABLE_REQUESTS)).isEqualTo(0)
                        assertThat(operations.fetchRequests(ctx, container, type, now, periodLength)).isEqualTo(0)

                        operations.incrementRequests(ctx, container, type, containerCreationTime, now, periodLength)
                        val result = requireNotNull(operations.dslContext(ctx).selectFrom(TABLE_REQUESTS)
                                .where(COLUMN_CONTAINER.eq(container))
                                .and(COLUMN_TYPE.eq(type))
                                .fetchOne())
                        assertThat(result[COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli())
                        assertThat(result[COLUMN_REQUESTS]).isEqualTo(1L)
                        assertThat(operations.fetchRequests(ctx, container, type, now, periodLength)).isEqualTo(1)

                        operations.incrementRequests(ctx, container, type, containerCreationTime, now.plusSeconds(30), periodLength)
                        val result2 = requireNotNull(operations.dslContext(ctx).selectFrom(TABLE_REQUESTS)
                                .where(COLUMN_CONTAINER.eq(container))
                                .and(COLUMN_TYPE.eq(type))
                                .fetchOne())
                        assertThat(result2[COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli())
                        assertThat(result2[COLUMN_REQUESTS]).isEqualTo(2L)
                        assertThat(operations.fetchRequests(ctx, container, type, now.plusSeconds(30), periodLength))
                                .isEqualTo(2)

                        assertThat(operations.fetchRequests(ctx, container, type, now.plusSeconds(periodLength.inWholeSeconds + 10), periodLength))
                                .isEqualTo(0)
                        operations.incrementRequests(ctx, container, type, containerCreationTime, now.plusSeconds(periodLength.inWholeSeconds + 10), periodLength)
                        val result3 = operations.dslContext(ctx).selectFrom(TABLE_REQUESTS)
                                .where(COLUMN_CONTAINER.eq(container))
                                .and(COLUMN_TYPE.eq(type))
                                .orderBy(COLUMN_PERIOD_START.asc())
                                .fetch()
                        assertThat(result3).hasSize(2)
                        assertThat(result3[0][COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli())
                        assertThat(result3[0][COLUMN_REQUESTS]).isEqualTo(2L)
                        assertThat(result3[1][COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli() + periodLength.inWholeMilliseconds)
                        assertThat(result3[1][COLUMN_REQUESTS]).isEqualTo(1L)
                        assertThat(operations.fetchRequests(ctx, container, type, now.plusSeconds(periodLength.inWholeSeconds + 10), periodLength))
                                .isEqualTo(1)

                        assertThat(operations.fetchRequests(ctx, container, type, now.plusSeconds(periodLength.inWholeSeconds * 5 + 10), periodLength))
                                .isEqualTo(0)
                        operations.incrementRequests(ctx, container, type, containerCreationTime, now.plusSeconds(periodLength.inWholeSeconds * 5 + 10), periodLength)
                        val result4 = operations.dslContext(ctx).selectFrom(TABLE_REQUESTS)
                                .where(COLUMN_CONTAINER.eq(container))
                                .and(COLUMN_TYPE.eq(type))
                                .orderBy(COLUMN_PERIOD_START.asc())
                                .fetch()
                        assertThat(result4).hasSize(3)
                        assertThat(result4[0][COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli())
                        assertThat(result4[0][COLUMN_REQUESTS]).isEqualTo(2L)
                        assertThat(result4[1][COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli() + periodLength.inWholeMilliseconds)
                        assertThat(result4[1][COLUMN_REQUESTS]).isEqualTo(1L)
                        assertThat(result4[2][COLUMN_PERIOD_START].time).isEqualTo(containerCreationTime.toEpochMilli() + periodLength.inWholeMilliseconds * 5)
                        assertThat(result4[2][COLUMN_REQUESTS]).isEqualTo(1L)
                        assertThat(operations.fetchRequests(ctx, container, type, now.plusSeconds(periodLength.inWholeSeconds * 5 + 10), periodLength))
                                .isEqualTo(1)

                        true
                    }
                }
    }
}
