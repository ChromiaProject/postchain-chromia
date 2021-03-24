package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "create a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it.")
class CommandProposeContainer: CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of new container",
            required = true)
    private var containerName = ""

    @Parameter(
            names = ["-cl", "--cluster"],
            description = "Name of cluster to put container in",
            required = true)
    private var clusterName = ""

    @Parameter(
            names = ["-d", "--deployer"],
            description = "Name of voter set authorized to operate in container.",
            required = true)
    private var deployerName = ""

    override fun key(): String = "propose-container"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).proposeContainer(containerName, clusterName, deployerName)
            Ok("proposal has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}