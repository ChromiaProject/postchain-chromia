package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.proposal_provider.proposeProviderStateOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption

class CommandProposeDisableProvider : CliktCommand(
        name = "disable",
        help = "Propose disabling an existing provider"
) {
    private val client by nopClientOption()

    private val key by requiredPubkeyOption()

    private val description by proposalDescriptionOption()

    override fun run() {
        client.transactionBuilder()
                .proposeProviderStateOperation(client.config.pubkey().data, key.data, false, description)
                .postAwaitConfirmation()
                .printResult(
                        "Disabling of provider has been proposed",
                        "Cannot propose disabling of provider"
                )
    }
}
