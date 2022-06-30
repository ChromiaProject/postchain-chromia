package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "proposes an update of a cluster's deployer. New deployer must be an existing voter set.")
class CommandProposeClusterDeployer : CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "name of new deployer.")
    private var deployer = ""


    @Parameter(
            names = ["-c", "--cluster"],
            description = "Name of cluster to update. Must exist in database",
            required = true)
    private var clusterName = ""

    override fun key(): String = "propose-cluster-deployer"

    override fun execute(): CliResult {

        return try {
            CliExecution(loadAppConfig()).proposeClusterDeployer(clusterName, deployer)
            Ok("proposal for deployer of cluster $clusterName has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}