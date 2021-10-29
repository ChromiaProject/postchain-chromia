package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "register new provider with given pubkey. default tier = 0. Tier-0 providers are enabled automatically, higher order tiers need voting to be enabled.")
class CommandRegisterProvider: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-t", "--tier"],
            description = "the provider's tier decides its level of authority",
            required = false)
    private var tier = 0L

    override fun key(): String = "provider-register"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).registerProvider(key, tier)
            Ok("Provider has been registered successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}