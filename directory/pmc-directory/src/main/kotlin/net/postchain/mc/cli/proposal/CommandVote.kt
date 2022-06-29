package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandVote : CliktCommand(
    name = "vote",
    help = "Providers decide if proposed configuration changes should be applied. Use this function to vote yes or no to a proposal."
) {

    private val nodeConfig by nodeConfigOption()

    private val idx by proposalIndexOption().required()

    private val vote by option("-y", "--approve", help = "Vote yes or no on this proposal")
        .flag("-n", "--revoke", default = true)

    override fun run() {
        CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).vote(idx, vote)
        println("Your vote is registered")
    }
}