package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "proposes an update of a voter set's governor. New governor must be an existing voter set.")
class CommandProposeVoterSetGovernor : CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "name of new governor.")
    private var governor = ""


    @Parameter(
            names = ["-v", "--voterset"],
            description = "Name of voter set to update. Must exist in database",
            required = true)
    private var voterSet = ""

    override fun key(): String = "voter-set-propose-governor"

    override fun execute(): CliResult {

        return try {
            CliExecution(loadAppConfig()).proposeVoterSetGovernor(voterSet, governor)
            Ok("governor proposal for voter set $voterSet has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}