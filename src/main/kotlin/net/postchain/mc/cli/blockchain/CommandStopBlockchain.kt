package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "stop blockchain")
class CommandStopBlockchain: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-r", "--remove-replicas"],
            description = "flag to remove replicas or not",
            required = false)
    private var removeReplicas = false

    override fun key(): String = "stop-blockchain"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).stopBlockchain(blockchainRID, removeReplicas)
            Ok("blockchain has been stop successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}