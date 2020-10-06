package net.postchain.mc.cli.consensusupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecution

@Parameters(commandDescription = "Block signers decide if proposed configuration changes should be applied. Use this function to vote yes or no to a proposition.")
class CommandVote: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-pk", "--node-pubkey"],
            description = "Node pubkey. Only the blocksigners (nodes) are allowed to propose and vote for configuration changes",
            required = true)
    private var pubkey = ""


    @Parameter(
            names = ["-h", "--height"],
            description = "block height at which new configuration will be applied",
            required = true)
    private var height = 0L

    @Parameter(
            names = ["-y", "--approve"],
            description = "to vote for, set this paramter to true. Else set it to false.",
            required = true)
    private var yes = true


    override fun key() = "vote"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).vote(blockchainRID, pubkey, height, yes)
            Ok("Your vote is registrated")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}