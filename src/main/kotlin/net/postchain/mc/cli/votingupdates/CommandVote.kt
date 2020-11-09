package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "Providers decide if proposed configuration changes should be applied. Use this " +
        "function to vote yes or no to a proposal.")
class CommandVote: CommandBase() {

    @Parameter(
            names = ["-idx", "--voting index"],
            description = "Unique index, used as reference to a proposed configuration update, of various type. Could be" +
                    " e.g. provider/node management or rell-module updates",
            required = true)
    private var idx = 0L

    @Parameter(
            names = ["-y", "--approve"],
            description = "to approve, set this paramter to true. Else set it to false.",
            required = false)
    private var yes = true

    override fun key() = "vote"

    override fun execute(): CliResult {
        return try {
            CliExecutionE0(loadAppConfig()).vote(idx, yes)
            Ok("Your vote is registered")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}