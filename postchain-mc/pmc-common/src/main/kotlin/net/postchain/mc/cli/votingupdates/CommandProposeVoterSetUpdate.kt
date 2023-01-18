package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.proposal.voter_set.proposeUpdateVoterSetOperation
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption

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
    private val newMember by option("--add-member", help = "Provider pubkey(s) to add to voter set")
            .convert { it.hexStringToByteArray() }
            .split(",")
            .default(listOf())
    private val removeMember by option("--remove-member", help = "Provider pubkey(s) to remove from voter set")
            .convert { it.hexStringToByteArray() }
            .split(",")
            .default(listOf())

    private val description by proposalDescriptionOption()

    override fun run() {
        client.transactionBuilder()
                .proposeUpdateVoterSetOperation(
                        client.config.pubkey().data,
                        voterSet, threshold, governor, newMember, removeMember, description
                )
                .postAwaitConfirmation()
                .printResult(
                        "Proposal for voter set $voterSet has been added",
                        "Failed to add proposal"
                )
    }
}
