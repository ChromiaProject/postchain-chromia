package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "list providers")
class CommandListProviders : CommandBase() {

    override fun key(): String = "list-providers"

    override fun execute(): CliResult {
        return try {
            val providers = CliExecutionC0(loadAppConfig()).listProviders()
            providers.forEach {
                val dict = it.asDict()
                println("pubkey: ${dict["pubkey"]!!.asByteArray().toHex()}")
                println("name: ${dict["name"]!!.asString()}")
                println("active: ${dict["active"]!!.asBoolean()}")
                println("beneficiary: ${dict["beneficiary"]!!.asByteArray().toHex()}")
            }
            Ok("List providers successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}