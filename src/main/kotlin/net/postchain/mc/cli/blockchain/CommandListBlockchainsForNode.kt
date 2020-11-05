package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.Chromia0CliExecution
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List blockchains for node")
class CommandListBlockchainsForNode : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "Node's public key",
            required = true)
    private var key = ""

    override fun key(): String = "list-blockchains-for-node"

    override fun execute(): CliResult {
        return try {
            val listBlockchains = CliExecution(loadAppConfig()).listBlockchainsForNode(key)
            listBlockchains.forEach { blockchain ->
                println(blockchain.toHex())
            }
            Ok("List blockchains successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}