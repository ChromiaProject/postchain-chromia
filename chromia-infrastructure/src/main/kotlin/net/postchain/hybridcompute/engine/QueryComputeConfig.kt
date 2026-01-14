package net.postchain.hybridcompute.engine

import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name

data class QueryComputeConfig(
        @param:Name("timeout_seconds")
        @param:DefaultValue(defaultLong = QUERY_COMPUTE_DEFAULT_TIMEOUT_SECONDS)
        val timeoutSeconds: Long = QUERY_COMPUTE_DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val QUERY_COMPUTE_DEFAULT_TIMEOUT_SECONDS = 3L
        val DEFAULT_CONFIG = QueryComputeConfig()
    }
}
