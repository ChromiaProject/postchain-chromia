package net.postchain.hybridcompute

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class HybridComputeSpecialTransactionExtensionTest {

    @Test
    fun isComputeClusterTimeout() {
        val extension = HybridComputeSpecialTransactionExtension()

        val computeClusterTimeoutSeconds = 10L
        extension.config = HybridComputeConfig(
                engine = "test-engine",
                loadTimeoutSeconds = 3,
                computeTimeoutSeconds = 5,
                computeClusterTimeoutSeconds = computeClusterTimeoutSeconds,
                concurrency = 1
        )
        val now = Instant.now().toEpochMilli()
        assertFalse(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000, now))
        assertTrue(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000 - 1, now))
    }
}

