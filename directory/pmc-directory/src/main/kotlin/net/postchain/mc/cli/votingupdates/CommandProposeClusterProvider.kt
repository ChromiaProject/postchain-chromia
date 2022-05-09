package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "proposes an update of a cluster's providers. add = false => remove provider from cluster. Cluster governance voter set has authority to update a cluster's providers")
class CommandProposeClusterProvider : CommandBase() {

    @Parameter(
            names = ["-k", "--pubkey"],
            description = "pubkey of provider to be added or removed.")
    private var provider = ""


    @Parameter(
            names = ["-c", "--cluster"],
            description = "Name of cluster to update. Must exist in database",
            required = true)
    private var clusterName = ""

    @Parameter(
            names = ["-a", "--add"],
            description = "boolean flag. Set to false if given provider should be removed",
            required = true)
    private var add = true

    override fun key(): String = "propose-cluster-provider"

    override fun execute(): CliResult {

        return try {
            CliExecution(loadAppConfig()).proposeClusterProvider(clusterName, provider, add)
            Ok("proposal for provider update of cluster $clusterName has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}