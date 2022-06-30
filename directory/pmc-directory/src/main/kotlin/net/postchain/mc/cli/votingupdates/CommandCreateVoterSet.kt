package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandCreateVoterSet : CliktCommand(
    name = "create",
    help = "Create a new voter set with a list of providers."
) {
    private val config by configOption()

    private val name by nameOption("Name of new voter set").required()

    private val providers by option(
        "-p", "--providers",
        help = "Comma separated list of pubkeys for this voter set"
    ).required()

    private val threshold by option(
        "-t", "--thresholds",
        help = """
        0: supermajority of voters, specifically  `n - (n - 1) / 3` (which is usually around 67%)
        -1: simple majority
        positive number: that many voters
    """.trimIndent()
    )
        .long().default(0L)
        .validate { require(it >= -1L) { "Threshold must be -1, 0 or a positive integer" } }

    private val governorName by option(
        "-g", "--governor",
        help = "Name of another voter set which can update this voter set. Default: voter set is its own governor."
    )

    override fun run() {
        CliExecution(config)
            .createVoterSet(name, providers, threshold, governorName)
        println("Voter set created")
    }
}