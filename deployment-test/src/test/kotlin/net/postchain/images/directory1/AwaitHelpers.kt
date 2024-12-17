package net.postchain.images.directory1

import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import net.postchain.client.core.TxRid
import net.postchain.common.tx.TransactionStatus
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Assertions.fail
import java.lang.Thread.sleep

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

fun awaitConfirmedTx(
        client: PostchainClient,
        txRid: TxRid,
        transactionName: String = "",
        retries: Int = 100,
        timeOut: java.time.Duration = java.time.Duration.ofSeconds(2)
): TransactionResult {
    repeat(retries) { attempt ->
        val txStatus = client.checkTxStatus(txRid)
        println("  Tx $transactionName result (attempt ${attempt}) was: ${txStatus.status}, code: ${txStatus.httpStatusCode}")
        if (txStatus.status == TransactionStatus.CONFIRMED) {
            return txStatus
        } else if (txStatus.status == TransactionStatus.REJECTED) {
            throw RuntimeException("Transaction $transactionName rejected")
        }
        sleep(timeOut.toMillis())
    }
    throw RuntimeException("Transaction $transactionName failed")
}