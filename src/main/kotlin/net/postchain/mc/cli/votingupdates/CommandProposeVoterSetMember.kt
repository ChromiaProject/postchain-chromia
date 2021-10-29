package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "proposes an update of a voter set's members. add = false => remove provider from vote set. Voter set's governor has authority to update members")
class CommandProposeVoterSetMember : CommandBase() {

    @Parameter(
            names = ["-k", "--pubkey"],
            description = "pubkey of provider to be added or removed.")
    private var provider = ""


    @Parameter(
            names = ["-v", "--voterset"],
            description = "Name of voter set to update. Must exist in database",
            required = true)
    private var vsName = ""

    @Parameter(
            names = ["-a", "--add"],
            description = "boolean flag. Set to false if given provider should be removed",
            required = true)
    private var add = true

    override fun key(): String = "voter-set-propose-member"

    override fun execute(): CliResult {

        return try {
            CliExecution(loadAppConfig()).proposeVoterSetMember(vsName, provider, add)
            Ok("proposal for member update of voter set $vsName has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}