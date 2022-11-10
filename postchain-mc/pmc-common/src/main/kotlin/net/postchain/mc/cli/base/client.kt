package net.postchain.mc.cli.base

import com.github.ajalt.clikt.output.TermUi.echo
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.mc.cli.util.NopPostchainClient

fun TransactionResult.printResult(onSuccess: String, onFail: String) {
    when (status) {
        TransactionStatus.CONFIRMED -> echo(onSuccess)
        TransactionStatus.REJECTED -> echo("$onFail: $rejectReason")
        TransactionStatus.WAITING -> echo("Transaction not complete")
        else -> echo("Cannot find status for this transaction")
    }
}

object ClientUtil {

    fun fromConfig(config: PostchainClientConfig): PostchainClient {
        return ConcretePostchainClientProvider().createClient(config)
    }

    fun nopClientFromConfig(config: PostchainClientConfig) = NopPostchainClient(ConcretePostchainClient(config))
}
