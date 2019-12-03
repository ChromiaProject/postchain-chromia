package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add replica")
class CommandAddReplica: CommandBase() {

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

    override fun key(): String = "add-replica"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addReplica(blockchainRID, key)
            Ok("Replica node has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}