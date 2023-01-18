package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalMessageOption

class CommandProposeEnableProvider : CliktCommand(
        name = "enable",
        help = "Propose enabling an existing provider"
) {
    private val client by nopClientOption()

    private val key by requiredPubkeyOption()

    private val message by proposalMessageOption()

    override fun run() {
        client.transactionBuilder()
                .proposeProviderStateOperation(client.config.pubkey().data, key.data, true, message)
                .postAwaitConfirmation()
                .printResult(
                        "Enabling of provider has been proposed",
                        "Cannot propose enabling of provider"
                )
    }
}