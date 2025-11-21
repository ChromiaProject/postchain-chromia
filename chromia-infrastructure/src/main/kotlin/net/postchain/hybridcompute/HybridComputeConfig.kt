package net.postchain.hybridcompute

import net.postchain.gtv.mapper.DefaultEmpty
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name

data class HybridComputeConfig(
        @DefaultValue(defaultString = "")
        val engine: String = "",

        @DefaultEmpty
        val engines: List<String> = listOf(),

        @DefaultValue(defaultLong = -1)
        @Name("load_timeout_seconds")
        val loadTimeoutSeconds: Long,

        @DefaultValue(defaultLong = -1)
        @Name("compute_timeout_seconds")
        val computeTimeoutSeconds: Long,

        @DefaultValue(defaultLong = -1)
        @Name("compute_cluster_timeout_seconds")
        val computeClusterTimeoutSeconds: Long,

        @DefaultValue(defaultLong = 1)
        val concurrency: Long,
)
