package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "propose removal of signers of a blockchain. Change will be applied after voting amongst providers.")
class CommandProposeRemoveBlockchainSigners: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-n", "--nodes"],
            description = "String of comma separated list of pubkey strings of nodes that no longer should be blocksigners for the given blockchain",
            required = true)
    private var nodes = ""

    override fun key() = "propose-remove-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            CliExecutionD1(loadAppConfig()).proposeRemoveBlockchainSigners(blockchainRID, nodes)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}