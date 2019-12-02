package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "disable existing provider by specific pubkey")
class CommandDisableProvider: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key() = "disable-provider"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).disableProvider(key)
            Ok("Provider has been disabled successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}