package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.proposal.proposeRemoveProviderOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption

class CommandProposeRemoveProvider : CliktCommand(
        name = "remove",
        help = "Propose removal of provider. Provider must be disabled. Command is irreversible"
) {
    private val client by nopClientOption()

    private val key by requiredPubkeyOption()

    private val description by proposalDescriptionOption()

    override fun run() {
        client.transactionBuilder()
                .proposeRemoveProviderOperation(
                        client.config.pubkey().data,
                        key.data,
                        description
                )
                .postAwaitConfirmation()
                .printResult(
                        "Provider remove proposition was added successfully",
                        "Cannot add proposal for removing provider"
                )
    }
}