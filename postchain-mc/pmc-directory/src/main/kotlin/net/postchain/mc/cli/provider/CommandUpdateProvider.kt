package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.directory1.updateProviderOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption

class CommandUpdateProvider : CliktCommand(
    name = "update",
    help = "update provider information"
) {
    private val client by nopClientOption()

    private val name by nameOption("Provider name").required()

    override fun run() {
        client.transactionBuilder()
            .updateProviderOperation(client.config.pubkey().data, name)
            .postSyncAwaitConfirmation()
            .printResult("Information updated",
            "Could not update provider data")
    }
}
