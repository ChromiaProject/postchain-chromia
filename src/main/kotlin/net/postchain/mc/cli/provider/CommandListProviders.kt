package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List all providers. Too see also inactive (disabled) providers, set flag -i.")
class CommandListProviders : CommandBase() {

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include disabled providers")
    private var includeInactive = false

    override fun key(): String = "list-providers"

    override fun execute(): CliResult {
        return try {
            val providers = CliExecution(loadAppConfig()).listProviders()
            PrintUtils.printProviders(providers)
            Ok("List providers successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }


}