package net.postchain.images.directory1

import org.awaitility.Duration
import org.awaitility.kotlin.await
import kotlin.test.assertTrue

internal fun <T> awaitQueryResult(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> T): T? {
    var result: T? = null
    var i = 0
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        try {
            i++
            System.out.println("  Try Query $i")
            result = assertion()
        } catch (ignore: Exception) {
            System.out.println("  Error from Query $i: ${ignore.message}")
            assertTrue(false)
        }
    }
    return result
}

internal fun awaitUntilAsserted(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> Unit) {
    var i = 0
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        i++
        System.out.println("  Try again: $i")
        assertion()
    }
}
