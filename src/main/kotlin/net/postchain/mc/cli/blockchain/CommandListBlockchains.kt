package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List blockchains. To include inactive ones, set flag -i.")
class CommandListBlockchains : CommandBase() {

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include inactive blockchains. A blockchain is inactivated with the command pause-blockchain.")
    private var includeInactive = false

    override fun key(): String = "list-blockchains"

    override fun execute(): CliResult {
        return try {
            val listBlockchains = CliExecution(loadAppConfig()).listBlockchains(includeInactive)
            listBlockchains.forEach { blockchain ->
                println(blockchain.toHex())
            }
            Ok("Listed blockchains successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}