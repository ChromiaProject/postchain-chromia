package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "create a new cluster that can hold containers with blockchains.")
class CommandAddCluster: CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of new voter set",
            required = true)
    private var name = ""

    @Parameter(
            names = ["-p", "--providers"],
            description = "String of comma separated list of pubkey strings of providers that should belong to this clsuter")
    private var providers = ""

    @Parameter(
            names = ["-g", "--governor"],
            description = "Name of another voter set which can update this voter set.",
            required = true)
    private var governorName = ""

    @Parameter(
            names = ["-d", "--deployers"],
            description = "Name of the voter set which can make updates in this cluster",
            required = true)
    private var deployersName = ""

    override fun key(): String = "add-cluster"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addCluster(name, providers, governorName, deployersName)
            Ok("Cluster has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}