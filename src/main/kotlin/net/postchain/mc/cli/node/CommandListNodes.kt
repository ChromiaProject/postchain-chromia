package net.postchain.mc.cli.node

import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "list nodes")
class CommandListNodes : CommandBase() {

    override fun key(): String = "list nodes"

    override fun execute(): CliResult {
        return try {
            val nodes = CliExecution(loadAppConfig()).listNodes()
            nodes.map { it.asDict() }.forEach { info ->
                println("Pubkey: ${info["pubkey"]?.asBoolean()}")
                println("Status: ${info["active"]?.asBoolean()}")
                println("Host: ${info["host"]?.asString()}")
                println("Port: ${info["port"]?.asInteger()}")
            }
            Ok("List nodes successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}