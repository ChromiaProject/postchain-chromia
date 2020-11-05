package net.postchain.mc.cli.node

import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.Chromia0CliExecution

@Parameters(commandDescription = "Get node list version")
class CommandGetNodeListVersion : CommandBase() {

    override fun key(): String = "get-node-list-version"

    override fun execute(): CliResult {
        return try {
            val version = Chromia0CliExecution(loadAppConfig()).getNodeListVersion()
            println("version: ${version}")
            Ok("Get node list version successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}