package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeVoterSetGovernor : CliktCommand(
    name = "update",
    help = "proposes an update of a voter set's governor. New governor must be an existing voter set."
) {
    private val nodeConfig by nodeConfigOption()

    private val governor by nameOption("Name of new governor").required()

    private val voterSet by option(
        "-v", "--voterset",
        help = "Name of existing voter set to update"
    ).required()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeVoterSetGovernor(voterSet, governor)
        println("governor proposal for voter set $voterSet has been added successfully")
    }
}