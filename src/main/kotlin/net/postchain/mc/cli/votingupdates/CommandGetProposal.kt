package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "Use this function to get information on a given proposal, before you vote.")
class CommandGetProposal: CommandBase() {

    @Parameter(
            names = ["-idx", "--proposal-index"],
            description = "Unique index, used as reference to a proposed configuration update, of various type. Could " +
                    "be e.g. provider/node management or rell-module updates",
            required = true)
    private var idx = 0L

    override fun key() = "get-proposal"

    override fun execute(): CliResult {
        return try {
            val proposal = CliExecutionE0(loadAppConfig()).getProposal(idx).asDict()
            val provPubkey = proposal["proposed_by"]!!.asByteArray().toHex()
            val proposedBy = CliExecutionE0(loadAppConfig()).getProviderInfo(provPubkey).asDict()
            val name = proposedBy["name"]!!.asString()
            println("proposal type: ${proposal["proposal_type"]!!.asString()}")
            println("index: ${proposal["rowid"]!!.asInteger()}")
            println("proposed by: ${proposal["proposed_by"]!!.asByteArray().toHex()}")
            if (name != "") {
                println("named : $name")
            }
            println("timestamp: ${proposal["timestamp"]!!.asInteger()}")
            Ok("Retrieved proposal info successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}