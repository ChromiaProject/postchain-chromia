package net.postchain.images.directory1

import org.awaitility.Duration
import org.awaitility.kotlin.await
import kotlin.test.assertTrue

internal fun <T> awaitQueryResult(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> T): T? {
    var result: T? = null
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        try {
            result = assertion()
        } catch (ignore: Exception) {
            assertTrue(false) // Will make sure we try again
        }
    }
    return result
}

internal fun awaitUntilAsserted(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> Unit) {
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        assertion()
    }
}
