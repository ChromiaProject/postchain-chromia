package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.promoteNodeProviderOperation
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
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

    private val system by option(help = "Proposes this provider as a system provider").flag()


    override fun run() {
        client.transactionBuilder()
            .run {
                if (system) proposeProviderIsSystemOperation(client.config.pubkey().key, key.key, true)
                else promoteNodeProviderOperation(client.config.pubkey().key, key.key)
            }
            .postSyncAwaitConfirmation()
            .printResult(
                "Provider was promoted",
                "Failed to promote provider"
            )
    }
}
