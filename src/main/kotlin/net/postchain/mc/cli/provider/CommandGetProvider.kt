package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "Get provider")
class CommandGetProvider : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key(): String = "get-provider"

    override fun execute(): CliResult {
        return try {
            val provider = CliExecution(loadAppConfig()).getProvider(key)
            println("provider name:  ${provider.get("name")?.asString()}")
            println("provider active:  ${provider.get("active")?.asBoolean()}")
            Ok("Get provider successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
