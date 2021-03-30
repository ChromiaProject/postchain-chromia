package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.gtv.Gtv
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "Get provider info")
class CommandGetProviderInfo : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    override fun key(): String = "get-provider-info"

    override fun execute(): CliResult {
        return try {
            val provider = CliExecution(loadAppConfig()).getProviderInfo(key)
            val providerList = arrayListOf<Gtv>(provider)
            PrintUtils.printProviders(providerList)

            val points = CliExecution(loadAppConfig()).listProvidersActionPoints(key)
            println("action points: $points")
            println("")

            Ok("Got provider info successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
