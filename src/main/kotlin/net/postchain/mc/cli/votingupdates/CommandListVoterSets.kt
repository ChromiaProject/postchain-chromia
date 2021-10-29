package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "List all existing voter set")
class CommandListVoterSets : CommandBase() {

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include disabled/removed voter sets (not implemented yet)")
    private var includeInactive = false

    override fun key() = "voter-sets-list"

    override fun execute(): CliResult {
        return try {
            val sets = CliExecutionD1(loadAppConfig()).listVoterSets()
            sets.forEach {
                println(it.asString())
            }
            Ok("Voter sets listed")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}