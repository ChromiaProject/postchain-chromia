package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandListProposalsSince : CliktCommand(
    name = "list",
    help = "List all active proposals since a given index"
) {
    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val config by configOption()
    private val id by proposalIndexOption().default(0L)

    override fun run() {
        val proposals = CliExecution(config).listProposalsSince(id)
        proposals.forEach {
            println("proposal type: ${it.proposalType}")
            println("index: ${it.rowid.id}")
        }
        if (proposals.isEmpty()) {
            println("There are no proposals waiting for approval.")
        }
    }
}