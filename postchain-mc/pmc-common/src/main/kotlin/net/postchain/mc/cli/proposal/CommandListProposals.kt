package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.voting.getProviderVotes
import net.postchain.chain0.proposal.getProposalsSince
import net.postchain.chain0.proposal.getRelevantProposals
import net.postchain.common.types.RowId
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandListProposals : CliktCommand(
        name = "list",
        help = "List all proposals that you can vote on"
) {
    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val client by nopClientOption()
    private val since by option(help = "List proposals since proposal id").long().default(0L)
    private val all by option(help = "Include proposals that you can not vote on").flag()

    override fun run() {
        val proposals = if (all) {
            client.getProposalsSince(RowId(since)).map { it.rowid to it.proposalType }
        } else {
            client.getRelevantProposals(client.pubkey, RowId(since)).map { it.rowid to it.proposalType }
        }
        if (proposals.isEmpty()) return echo("No proposals found")

        val votes = client.getProviderVotes(client.pubkey)

        table {
            header("Type", "Id", "Your vote")
            proposals.forEach { (rowid, proposalType) ->
                val vote = votes.find { it.proposal == rowid }
                val voteStatus = if (vote == null) "No vote registered" else if (vote.vote) "Accept" else "Reject"
                row(proposalType.toString(), rowid.id.toString(), voteStatus)
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { echo(it) }
    }
}
