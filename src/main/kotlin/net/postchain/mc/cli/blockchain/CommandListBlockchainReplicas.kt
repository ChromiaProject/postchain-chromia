package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "List blockchain replicas")
class CommandListBlockchainReplicas : CommandBase() {

    override fun key(): String = "list-blockchain-replicas"

    override fun execute(): CliResult {
        return try {
            val listBlockchains = CliExecution(loadAppConfig()).listBlockchainReplicas()
            listBlockchains.forEach { blockchain ->
                println(blockchain.toHex())
            }
            Ok("List blockchain replicas successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}