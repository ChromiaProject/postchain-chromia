package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "Providers decide if proposed configuration changes should be applied. This function adds an initial provider that can approve things.")
class CommandInit: CommandBase() {

    override fun key() = "init"

    override fun execute(): CliResult {
        return try {
            CliExecutionE0(loadAppConfig()).init()
            Ok("You have a initial provider that can vote for updates.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}