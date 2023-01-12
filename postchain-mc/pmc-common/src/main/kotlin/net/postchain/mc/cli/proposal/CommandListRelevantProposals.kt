package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.proposal.getRelevantProposals
import net.postchain.chain0.common.voting.getProviderVotes
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandListRelevantProposals : CliktCommand(
        name = "list",
        help = "List all proposals that you can vote on"
) {

    private val client by nopClientOption()

    override fun run() {
        val proposals = client.getRelevantProposals(client.pubkey)
        if (proposals.isEmpty()) return println("There are no proposals that you can vote on")

        val votes = client.getProviderVotes(client.pubkey)

        table {
            header("Type", "Id", "Your vote")
            proposals.forEach { proposal ->
                val vote = votes.find { it.proposal == proposal.rowid }
                val voteStatus = if (vote == null) "No vote registered" else if (vote.vote) "Accept" else "Reject"
                row(proposal.proposalType.toString(), proposal.rowid.id.toString(), voteStatus)
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { echo(it) }
    }
}
