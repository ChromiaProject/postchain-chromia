package net.postchain.hybridcompute

import net.postchain.gtv.mapper.DefaultEmpty
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name

data class HybridComputeConfig(
        @DefaultValue(defaultString = "")
        val engine: String = "",

        @DefaultEmpty
        val engines: List<String> = listOf(),

        @DefaultEmpty
        @Name("fast_engines")
        val fastEngines: List<String> = listOf(),

        @DefaultValue(defaultLong = -1)
        @Name("compute_cluster_timeout_seconds")
        val computeClusterTimeoutSeconds: Long,

        @DefaultValue(defaultLong = 60 * 1000) // one minute
        @Name("block_building_interval_millis")
        val blockBuildingIntervalMillis: Long,

        @DefaultValue(defaultLong = 1)
        val concurrency: Long,
)
