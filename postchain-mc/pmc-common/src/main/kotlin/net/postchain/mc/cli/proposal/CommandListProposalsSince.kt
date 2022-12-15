package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.Table
import de.m3y.kformat.table
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
        if (proposals.isEmpty()) return println("There are no proposals waiting for approval.")
        table {
            header("Type", "Id")
            proposals.forEach {
                row(it.proposalType.toString(), it.rowid.id.toString())
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { echo(it) }
    }
}