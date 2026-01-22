package net.postchain.hybridcompute

import net.postchain.gtv.mapper.DefaultEmpty
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name

data class HybridComputeConfig(
        @param:DefaultValue(defaultString = "")
        val engine: String = "",

        @param:DefaultEmpty
        val engines: List<String> = listOf(),

        @param:DefaultEmpty
        @param:Name("fast_engines")
        val fastEngines: List<String> = listOf(),

        @param:DefaultValue(defaultLong = -1)
        @param:Name("compute_cluster_timeout_seconds")
        val computeClusterTimeoutSeconds: Long,

        @param:DefaultValue(defaultLong = 60 * 1000) // one minute
        @param:Name("block_building_interval_millis")
        val blockBuildingIntervalMillis: Long,

        @param:DefaultValue(defaultLong = 1)
        val concurrency: Long,
)
