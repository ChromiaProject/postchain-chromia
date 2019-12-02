package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

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
            CliExecution(loadAppConfig()).registerProvider(key)
            Ok("Provider has been registered successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}