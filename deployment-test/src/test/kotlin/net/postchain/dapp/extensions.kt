package net.postchain.dapp

import net.postchain.client.core.TransactionResult
import net.postchain.client.transaction.Postable
import net.postchain.common.tx.TransactionStatus
import net.postchain.images.directory1.testLogger
import java.lang.Thread.sleep
import java.time.Duration

/**
 * When we call the "client" it will try 100 times to get the status for the TX, so
 * we don't have to do much ourselves here.
 *
 * We will repeat the TX ONLY if we get REJECTED, b/c that's a serious problem
 *
 * (WAITING is considered successful, but it could mean that something is wrong since the
 * client tried 100 times and still didn't get "CONFIRMED".)
 *
 */
fun Postable.postTransactionUntilConfirmed(
    transactionName: String = "",
    retries: Int = 100,
    timeOut: Duration = Duration.ofSeconds(2)
): TransactionResult {
    repeat(retries) { attempt ->
        val txRes = postAwaitConfirmation()
        if (txRes.status == TransactionStatus.REJECTED && isRetryableStatus(txRes.httpStatusCode) ||
            txRes.status == TransactionStatus.UNKNOWN
        ) {
            testLogger.info { "  Tx $transactionName result (attempt ${attempt}) was: ${txRes.status}, code: ${txRes.httpStatusCode}" }
            sleep(timeOut.toMillis())
        } else {
            testLogger.info { "  TX $transactionName result was: ${txRes.status}, code: ${txRes.httpStatusCode} ${txRes.rejectReason?.let { ", reason: $it" } ?: ""}" }
            return txRes
        }
    }
    throw RuntimeException("Transaction $transactionName failed")
}

private fun isRetryableStatus(status: Int?) = when (status) {
    404, 503, 500 -> true
    else -> false
}
