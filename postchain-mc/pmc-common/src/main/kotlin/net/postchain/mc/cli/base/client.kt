package net.postchain.mc.cli.base

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.*
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.SigMaker
import net.postchain.mc.cli.util.NopPostchainClient
import net.postchain.mc.config.app.ClientConfig

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
