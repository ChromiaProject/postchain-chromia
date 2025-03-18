package net.postchain.hybridcompute

import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name

data class HybridComputeConfig(
        val engine: String,

        @Name("compute_timeout_seconds")
        val computeTimeoutSeconds: Long,

        @DefaultValue(defaultLong = 1)
        val concurrency: Long,
)
