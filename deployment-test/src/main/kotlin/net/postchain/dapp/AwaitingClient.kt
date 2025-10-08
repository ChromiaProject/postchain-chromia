package net.postchain.dapp

import junit.framework.TestCase.fail
import net.postchain.client.core.PostchainClient
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import org.awaitility.Duration
import org.awaitility.kotlin.await

class AwaitingClient(val client: PostchainClient): PostchainClient by client {
    override fun query(name: String, args: Gtv): Gtv {
        return awaitQueryResult { client.query(name, args) } ?: GtvNull
    }
}

internal fun <T> awaitQueryResult(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> T): T? {
    var result: T? = null
    await.pollInterval(Duration.ONE_SECOND).atMost(atMost).untilAsserted {
        try {
            result = assertion()
        } catch (_: Exception) {
            fail() // Will make sure we try again
        }
    }
    return result
}