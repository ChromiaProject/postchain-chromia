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
import net.postchain.mc.cli.util.proposalMessageOption
import net.postchain.mc.cli.util.pubkeyOption

class CommandPromoteProvider : CliktCommand(
        name = "promote",
        help = "Gives a provider access to add signer nodes to clusters"
) {
    private val client by nopClientOption()
    private val key by pubkeyOption("Public key of provider to promote").required()

    private val system by option(help = "Proposes this provider as a system provider").flag()

    private val message by proposalMessageOption()

    override fun run() {
        client.transactionBuilder()
                .run {
                    if (system) proposeProviderIsSystemOperation(client.pubkey, key.data, true, message)
                    else promoteNodeProviderOperation(client.pubkey, key.data)
                }
                .postAwaitConfirmation()
                .printResult(
                        "Provider was promoted",
                        "Failed to promote provider"
                )
    }
}
