package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandListProposalsSince : CliktCommand(
        name = "list",
        help = "List all active proposals since a given index"
) {

    private val config by configOption()
    private val idx by proposalIndexOption().default(0L)

    override fun run() {
        val proposals = CliExecution(config).listProposalsSince(idx)
        proposals.forEach {
            val n = it.asDict()
            println("proposal type: ${n["proposal_type"]!!.asString()}")
            println("index: ${n["rowid"]!!.asInteger()}")
        }
        if (proposals.isEmpty()) {
            println("There are no proposals waiting for approval.")
        }
    }
}