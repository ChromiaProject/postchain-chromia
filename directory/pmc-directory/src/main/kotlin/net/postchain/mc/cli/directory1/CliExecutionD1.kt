package net.postchain.mc.cli.directory1

import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.gtv.*
import net.postchain.mc.cli.common0.CliExecution

class CliExecutionD1(config: PostchainClientConfig) : CliExecution(config) {

    companion object : KLogging()


    /**
     * This operation initializes the database with a first provider. If table `providers` is empty, the public key from
     * the module argument is registered as a first provider and enabled. Why? The system needs at least one provider,
     * that can vote for update proposals.
     */
    fun initAsync() : TransactionBuilder {
        return makeTransactionWithNop().addOperation("init")
    }
    fun init() {
        sendTxSync(initAsync(), "Initial provider added and enabled",
                "Cannot add and enable initial provider")
    }



    fun updateProvider(key: String, name: String) {
        sendTxSync(updateProviderAsync(key, name), "Provider data has been updated",
                "Cannot update provider")
    }

    fun updateProviderAsync(key: String, name: String): TransactionBuilder {
        val provider = providerGtv(key)
        var data: Array<Gtv> = arrayOf(provider)
        if (name.isNotEmpty()) {
            data = data.plus(GtvFactory.gtv(name))
        } else {
            data = data.plus(GtvNull)
        }
        return makeTransactionWithNop().addOperation("update_provider_data", *data)
    }

}