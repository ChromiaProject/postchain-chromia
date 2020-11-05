package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.Chromia0CliExecution

@Parameters(commandDescription = "register provider by specific pubkey")
class CommandRegisterProvider: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key(): String = "register-provider"

    override fun execute(): CliResult {
        return try {
            Chromia0CliExecution(loadAppConfig()).registerProvider(key)
            Ok("Provider has been registered successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}