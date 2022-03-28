package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "update name of provider")
class CommandUpdateProviderName: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-n", "--name"],
            description = "provider's name",
            required = false)
    private var name = ""

    override fun key() = "update-provider-name"

    override fun execute(): CliResult {
        return try {
            CliExecutionD1(loadAppConfig()).updateProvider(key, name)
            Ok("Provider has been renamed successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}