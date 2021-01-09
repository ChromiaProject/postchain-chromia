package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "list nodes by provider")
class CommandListProviderNodes : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key(): String = "list-provider-nodes"

    override fun execute(): CliResult {
        return try {
            val nodes = CliExecution(loadAppConfig()).listNodesByProvider(key)
                nodes.forEach { info ->
                println("host: ${info["host"]?.asString()}")
                println("port: ${info["port"]?.asInteger()}")
                println("pubkey: ${info["pubkey"]?.asByteArray()?.toHex()}")
                println("last_update: ${info["last_update"]?.asInteger()}")
            }
            Ok("List nodes by provider successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}