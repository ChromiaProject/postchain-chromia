package net.postchain.hybridcompute

import net.postchain.core.EContext
import java.time.Instant
import kotlin.time.Duration

class MockDatabaseOperations : HybridComputeDatabaseOperations {
    override fun initialize(ctx: EContext) {}
    override fun fetchRequests(ctx: EContext, container: String, type: String, now: Instant, periodLength: Duration): Long = 0L

    override fun incrementRequests(ctx: EContext, container: String, type: String,
                                   containerCreationTime: Instant?, now: Instant, periodLength: Duration) {}
}
