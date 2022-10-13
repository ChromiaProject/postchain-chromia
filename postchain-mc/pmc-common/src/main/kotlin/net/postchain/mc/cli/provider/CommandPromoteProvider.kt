package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.promoteNodeProviderOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption

class CommandPromoteProvider : CliktCommand(
    name = "promote",
    help = "Gives a provider access to add signer nodes to clusters"
) {
    private val client by nopClientOption()
    private val key by pubkeyOption().required()


    override fun run() {
        client.transactionBuilder()
            .promoteNodeProviderOperation(client.config.pubkey().key, key.key)
            .postSync()
            .printResult(
                "Provider was promoted",
                "Failed to promote provider"
            )
    }
}
