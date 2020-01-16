package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

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
            val provider = CliExecution(loadAppConfig()).getProviderInfo(key).asDict()
            println("provider pubkey:  ${provider["pubkey"]?.asByteArray()?.toHex()}")
            println("provider name:  ${provider["name"]?.asString()}")
            println("provider status:  ${provider["active"]?.asBoolean()}")
            Ok("Get provider info successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
