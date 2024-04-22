package net.postchain.images.directory1

import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.fail

fun <T> awaitQueryResult(atMost: Duration = Duration.FIVE_MINUTES, assertion: () -> T): T? {
    var result: T? = null
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        try {
            result = assertion()
        } catch (ignore: Exception) {
            fail() // Will make sure we try again
        }
    }
    return result
}

fun awaitUntilAsserted(atMost: Duration = Duration.FIVE_MINUTES, assertion: () -> Unit) {
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        assertion()
    }
}
