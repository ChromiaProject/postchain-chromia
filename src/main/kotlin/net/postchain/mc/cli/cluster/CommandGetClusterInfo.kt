package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.PrintUtils.printClusters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "Get information about a cluster")
class CommandGetClusterInfo : CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of cluster.",
            required = true)
    private var name = ""

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include disabled/removed clusters (not implemented yet)")
    private var includeInactive = false

    override fun key() = "cluster-info"

    override fun execute(): CliResult {
        return try {
            val clusterInfo = CliExecutionD1(loadAppConfig()).getClusterInfo(name)
            printClusters(listOf(clusterInfo))
            Ok("Query returned successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}