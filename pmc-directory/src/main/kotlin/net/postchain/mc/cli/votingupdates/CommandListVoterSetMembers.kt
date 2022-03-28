package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "List members of the given voter set")
class CommandListVoterSetMembers: CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of voter set. Existing names can be listed with voter-sets-list",
            required = true)
    private var name = ""

    override fun key() = "list-voter-set-members"

    override fun execute(): CliResult {
        return try {
            val members = CliExecutionD1(loadAppConfig()).listVoterSetMembers(name)
            members.forEach {
                println(it.asByteArray().toHex())
            }
            if (members.isEmpty()) {
                Ok("Voter set has no members.")
            } else {
                Ok("Voter set members listed")
            }
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}