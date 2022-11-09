package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandVote : CliktCommand(
    name = "vote",
    help = "Providers decide if proposed configuration changes should be applied. Use this function to vote yes or no to a proposal."
) {

    private val config by configOption()

    private val id by proposalIndexOption().required()

    private val vote by option("-y", "--accept", help = "Accept or reject this proposal")
        .flag("-n", "--reject", default = true)

    override fun run() {
        CliExecution(config).vote(id, vote)
        println("Your vote is registered")
    }
}