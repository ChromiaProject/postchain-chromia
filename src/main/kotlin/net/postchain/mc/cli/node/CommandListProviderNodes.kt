package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "list nodes by provider. To see also inactive nodes, set flag -i.")
class CommandListProviderNodes : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include inactive nodes")
    private var includeInactive = false

    override fun key(): String = "list-provider-nodes"

    override fun execute(): CliResult {
        return try {
            val nodes = CliExecution(loadAppConfig()).listNodesByProvider(key)
            PrintUtils.printNodes(nodes, includeInactive, false)
            Ok("List nodes by provider successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}