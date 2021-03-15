package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "Create a new voter set with a list of providers. All important changes are done using voter sets. ")
class CommandCreateVoterSet: CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of new voter set",
            required = true)
    private var name = ""

    @Parameter(
            names = ["-p", "--providers"],
            description = "String of comma separated list of pubkey strings of initial voters")
    private var providers = ""

    @Parameter(
            names = ["-t", "--threshold"],
            description = "0: supermajority of voters, specifically  `n - (n - 1) / 3` (which is usually around 67%)\n" +
                    "\t// -1: simple majority\n" +
                    "\t// positive number: that many voters")
    private var threshold = 0L

    @Parameter(
            names = ["-g", "--governor"],
            description = "Name of another voter set which can update this voter set. Default: voter set is its own governor.")
    private var governorName = ""

    override fun key() = "create-voter-set"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).createVoterSet(name, providers, threshold, governorName)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}