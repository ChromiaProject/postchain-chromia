package net.postchain.dapp

import net.postchain.client.core.PostchainClient
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.fail

class AwaitingClient(val client: PostchainClient): PostchainClient by client {
    override fun query(name: String, args: Gtv): Gtv {
        return awaitQueryResult { client.query(name, args) } ?: GtvNull
    }

    override fun transactionBuilder(initialSigners: List<KeyPair>, remainingRequiredSigners: List<PubKey>): TransactionBuilder {
        return client.transactionBuilder(initialSigners, remainingRequiredSigners)
    }
}

internal fun <T> awaitQueryResult(atMost: Duration = Duration.TWO_MINUTES, assertion: () -> T): T? {
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
