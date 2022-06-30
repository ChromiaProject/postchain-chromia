package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.common.toHex
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandGetProposal : CliktCommand(
    name = "info",
    help = "Gets information of a given proposal"
) {
    private val config by configOption()

    private val idx by proposalIndexOption().required()

    override fun run() {
        val cliExecution = CliExecutionD1(config)
        val proposal = cliExecution.getProposal(idx).asDict()
        val provPubkey = proposal["proposed_by"]!!.asByteArray().toHex()
        val proposedBy = cliExecution.getProviderInfo(provPubkey).asDict()
        val name = proposedBy["name"]!!.asString()
        println("proposal type: ${proposal["proposal_type"]!!.asString()}")
        println("index: ${proposal["rowid"]!!.asInteger()}")
        println("proposed by: ${proposal["proposed_by"]!!.asByteArray().toHex()}")
        if (name != "") {
            println("named : $name")
        }
        println("timestamp: ${proposal["timestamp"]!!.asInteger()}")
    }
}