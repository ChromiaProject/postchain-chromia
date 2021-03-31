package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "transfer some of your action points to another provider")
class CommandTransferActionPoints: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-a", "--amount"],
            description = "number of points to transfer",
            required = true)
    private var amount = 0L

    override fun key(): String = "transfer-action-points"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).transferActionPoints(key, amount)
            Ok("Action points have been transferred successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}