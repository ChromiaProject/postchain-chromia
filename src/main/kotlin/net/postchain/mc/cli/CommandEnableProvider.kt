package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "enable existing provider by specific pubkey")
class CommandEnableProvider: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key() = "enable-provider"

    override fun execute(): CliResult {
        return try {
            CliExecution().enableProvider(loadAppConfig(), key)
            Ok("Provider has been enabled successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}