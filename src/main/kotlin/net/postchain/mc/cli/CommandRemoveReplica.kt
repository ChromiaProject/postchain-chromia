package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "remove replica")
class CommandRemoveReplica: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-k", "--key"],
            description = "Node's public key",
            required = true)
    private var key = ""

    override fun key(): String = "remove-replica"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).removeReplica(blockchainRID, key)
            Ok("Replica node has been removed successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}