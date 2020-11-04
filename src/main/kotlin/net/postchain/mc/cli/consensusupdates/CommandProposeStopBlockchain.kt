package net.postchain.mc.cli.consensusupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecution

@Parameters(commandDescription = "propose stopping a blockchain. Change will be applied after voting amongst providers.")
class CommandProposeStopBlockchain: CommandBase() {

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

    override fun key() = "propose-stop-blockchain"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).proposeStopBlockchain(blockchainRID, removeReplicas)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}