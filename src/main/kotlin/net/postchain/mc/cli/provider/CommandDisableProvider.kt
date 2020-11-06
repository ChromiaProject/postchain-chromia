package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

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
            CliExecutionC0(loadAppConfig()).disableProvider(key)
            Ok("Provider has been disabled successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}