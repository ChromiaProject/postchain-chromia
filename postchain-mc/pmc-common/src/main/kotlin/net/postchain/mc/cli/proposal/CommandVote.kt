package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandVote : CliktCommand(
    name = "vote",
    help = "Providers decide if proposed configuration changes should be applied. Use this function to vote yes or no to a proposal."
) {

    private val config by lazy { read() }

    private val idx by proposalIndexOption().required()

    private val vote by option("-y", "--approve", help = "Vote yes or no on this proposal")
        .flag("-n", "--revoke", default = true)

    override fun run() {
        CliExecution(config).vote(idx, vote)
        println("Your vote is registered")
    }
}