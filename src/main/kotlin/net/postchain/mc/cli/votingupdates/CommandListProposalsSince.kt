package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "Providers decide if proposed configuration changes should be applied. Use this function to vote yes or no to a proposal.")
class CommandListProposalsSince: CommandBase() {

    @Parameter(
            names = ["-idx", "--proposal index"],
            description = "Unique index, used as reference to a proposed configuration update, of various type. Could be e.g. provider/node management or rell-module updates",
            required = false)
    private var idx = 0L

    override fun key() = "list-proposals-since"

    override fun execute(): CliResult {
        return try {
            val proposals = CliExecutionE0(loadAppConfig()).listProposalsSince(idx)
            proposals.forEach {
                val n = it.asDict()
                println("proposal type: ${n["proposal_type"]!!.asString()}")
                println("index: ${n["rowid"]!!.asInteger()}")
            }
            Ok("Listed proposals are waiting for votes")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}