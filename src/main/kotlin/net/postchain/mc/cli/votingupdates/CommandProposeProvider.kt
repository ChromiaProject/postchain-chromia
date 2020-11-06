package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "propose new provider. Providers can add nodes add vote for different configuration updates, such as new providers, new nodes or new blockchains.")
class CommandProposeProvider: CommandBase() {

    @Parameter(
            names = ["-pk", "--pubkey"],
            description = "pubkey of the new provider",
            required = true)
    private var key = ""

    override fun key() = "propose-provider"

    override fun execute(): CliResult {
        return try {
            CliExecutionE0(loadAppConfig()).proposeProvider(key)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}