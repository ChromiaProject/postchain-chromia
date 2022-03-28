package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "propose disabling of provider. Providers can add nodes add vote for different configuration updates, such as new providers, new nodes or new blockchains.")
class CommandProposeDisableProvider: CommandBase() {

    @Parameter(
            names = ["-pk", "--pubkey"],
            description = "pubkey of the provider to be disabled",
            required = true)
    private var key = ""

    override fun key() = "propose-disable-provider"

    override fun execute(): CliResult {
        return try {
            CliExecutionD1(loadAppConfig()).proposeDisableProvider(key)
            Ok("Proposal is registered.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}