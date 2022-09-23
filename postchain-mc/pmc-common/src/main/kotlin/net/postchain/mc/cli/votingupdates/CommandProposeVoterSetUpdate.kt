package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeVoterSetUpdate : CliktCommand(
    name = "update",
    help = "proposes an update of a voter set's governor. New governor must be an existing voter set."
) {
    private val client by nopClientOption()

    private val voterSet by option(
        "-vs", "--voter-set",
        help = "Name of existing voter set to update"
    ).required()

    private val threshold by option("--threshold", help = "New threshold").long()
    private val governor by option("--governor", help = "Name of new governor")
    private val newMember by option("--add-member", help = "Provider pubkey to add to voter set")
    private val removeMember by option("--remove-member", help = "Provider pubkey to remove from voter set")

    override fun run() {
        client.transactionBuilder()
            .proposeUpdateVoterSetOperation(
                client.config.pubkey().key,
                voterSet, threshold, governor, newMember?.hexStringToByteArray(), removeMember?.hexStringToByteArray()
            )
            .postSyncAwaitConfirmation()
            .printResult(
                "Proposal for voter set $voterSet has been added",
                "Failed to add proposal"
            )
    }
}
