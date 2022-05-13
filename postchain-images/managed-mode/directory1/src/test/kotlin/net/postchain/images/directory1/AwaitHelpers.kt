package net.postchain.images.directory1

import org.awaitility.Duration
import org.awaitility.kotlin.await

internal fun <T> awaitUntilAsserted(atMost: Duration = Duration.FIVE_MINUTES, assertion: () -> T): T? {
    var result: T? = null
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        try {
            result = assertion()
        } catch (ignore: Exception) {
        }
    }
    return result
}

internal fun <T> awaitQueryResult(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> T) =
        awaitUntilAsserted(atMost, assertion)

