package net.postchain.hybridcompute

import net.postchain.core.EContext
import java.time.Instant
import kotlin.time.Duration

interface HybridComputeDatabaseOperations {
    fun initialize(ctx: EContext)
    fun fetchPoints(ctx: EContext, container: String, type: String, now: Instant, periodLength: Duration): Long
    fun incrementPoints(ctx: EContext, container: String, type: String,
                        containerCreationTime: Instant?, now: Instant, periodLength: Duration, pointsConsumed: Long)
}
