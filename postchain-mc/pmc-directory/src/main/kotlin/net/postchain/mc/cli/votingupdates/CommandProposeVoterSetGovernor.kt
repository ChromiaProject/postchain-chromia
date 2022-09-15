package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeVoterSetGovernor : CliktCommand(
    name = "update",
    help = "proposes an update of a voter set's governor. New governor must be an existing voter set."
) {
    private val config by configOption()

    private val governor by nameOption("Name of new governor").required()

    private val voterSet by option(
        "-v", "--voterset",
        help = "Name of existing voter set to update"
    ).required()

    override fun run() {
        CliExecution(config).proposeVoterSetGovernor(voterSet, governor)
        println("governor proposal for voter set $voterSet has been added successfully")
    }
}