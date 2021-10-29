package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "Get governor of a voter set")
class CommandGetVoterSetGovernor : CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "name of voter set",
            required = true)
    private var name = ""


    override fun key(): String = "voter-set-governor-info"

    override fun execute(): CliResult {
        return try {
            val governor = CliExecution(loadAppConfig()).getVoterSetGovernor(name)
                println("Governor of voter set: $governor")
            Ok("Query returned successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
