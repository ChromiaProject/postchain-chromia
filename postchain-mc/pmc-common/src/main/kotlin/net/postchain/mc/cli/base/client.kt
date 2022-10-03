package net.postchain.mc.cli.base

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.mc.cli.util.NopPostchainClient

fun TransactionResult.printResult(onSuccess: String, onFail: String) {
    when (status) {
        TransactionStatus.CONFIRMED -> println(onSuccess)
        TransactionStatus.REJECTED -> println("$onFail: $rejectReason")
        TransactionStatus.WAITING -> println("Transaction not complete")
        else -> println("Cannot find status for this transaction")
    }
}

object ClientUtil {

    fun fromConfig(config: PostchainClientConfig): PostchainClient {
        return ConcretePostchainClientProvider().createClient(config)
    }

    fun nopClientFromConfig(config: PostchainClientConfig) = NopPostchainClient(ConcretePostchainClient(config))
}
