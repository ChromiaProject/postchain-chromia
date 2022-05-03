package net.postchain.mc.cli.replica

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "remove replica of this container from this cluster")
class CommandRemoveContainerReplica: CommandBase() {

    @Parameter(
            names = ["-cl", "--cluster"],
            description = "cluster name",
            required = true)
    private var clusterName = ""

    @Parameter(
            names = ["-co", "--container"],
            description = "container name",
            required = true)
    private var containerName = ""

    override fun key(): String = "remove-container-replica"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).removeContainerReplica(clusterName, containerName)
            Ok("Container replica has been removed from cluster successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}