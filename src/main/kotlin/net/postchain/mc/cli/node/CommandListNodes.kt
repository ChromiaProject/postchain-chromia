package net.postchain.mc.cli.node

import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "list nodes")
class CommandListNodes : CommandBase() {

    override fun key(): String = "list-nodes"

    override fun execute(): CliResult {
        return try {
            val nodes = CliExecution(loadAppConfig()).listNodes()
            nodes.forEach { info ->
                println("host: ${info.get(0)?.asString()}")
                println("port: ${info.get(1)?.asInteger()}")
                println("pubkey: ${info[2]?.asByteArray().toHex()}")
                println("last_update: ${info[3]?.asInteger()}")
            }
            Ok("List nodes successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}