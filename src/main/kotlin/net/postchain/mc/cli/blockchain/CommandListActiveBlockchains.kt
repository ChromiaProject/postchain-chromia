package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "List active blockchains")
class CommandListActiveBlockchains : CommandBase() {

    override fun key(): String = "list-active-blockchains"

    override fun execute(): CliResult {
        return try {
            val listBlockchains = CliExecution(loadAppConfig()).listActiveBlockchains()
            listBlockchains.forEach { blockchain ->
                println(blockchain.toHex())
            }
            Ok("List active blockchains successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}