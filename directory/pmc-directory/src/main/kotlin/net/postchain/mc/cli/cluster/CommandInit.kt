package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "Create system cluster with naked system container for the directory blockchain. " +
        "Module argument initial_provider becomes first member of SYSTEM_P voter set.")
class CommandInit: CommandBase() {

    override fun key() = "initialize"

    override fun execute(): CliResult {
        return try {
            CliExecutionD1(loadAppConfig()).init()
            Ok("You have an initial provider that can vote for updates.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}