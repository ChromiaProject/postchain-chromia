package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.voting.createVoterSetOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.pubkeysOption

class CommandCreateVoterSet : CliktCommand(
        name = "create",
        help = "Create a new voter set with a list of providers."
) {
    private val client by clientOption()

    private val name by nameOption("Name of new voter set").required()

    private val pubkeys by pubkeysOption("Comma separated list of provider pubkeys for this voter set")
            .required()

    private val threshold by option(
            "-t", "--threshold",
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
        client.transactionBuilder()
                .createVoterSetOperation(
                        client.config.pubkey().data,
                        name,
                        threshold,
                        pubkeys.map { it.data },
                        governorName
                )
                .postAwaitConfirmation()
                .printResult(
                        "Voter set created",
                        "Cannot create voter set"
                )
    }
}