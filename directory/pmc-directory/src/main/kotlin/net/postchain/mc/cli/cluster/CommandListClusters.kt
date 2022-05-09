package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "List all existing clusters")
class CommandListClusters : CommandBase() {

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include disabled/removed clusters (not implemented yet)")
    private var includeInactive = false

    override fun key() = "list-clusters"

    override fun execute(): CliResult {
        return try {
            val clusters = CliExecutionD1(loadAppConfig()).listClusters()
            clusters.forEach {
                println(it.asString())
            }
            Ok("Query returned successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}