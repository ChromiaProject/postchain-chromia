package net.postchain.mc.cli.replica

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "add replica of a container to this cluster. (Another cluster is responsible for blockbuilding)")
class CommandAddContainerReplica: CommandBase() {

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

    override fun key(): String = "cluster-add-container-replica"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addContainerReplica(blockchainRID, key)
            Ok("Replica of blockchain has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}