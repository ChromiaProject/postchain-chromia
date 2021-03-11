package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.enterprise0.CliExecutionE0

@Parameters(commandDescription = "propose a new blockchain in a specific container. Change will be applied after voting amongst providers.")
class CommandProposeBlockchain: CommandBase() {

    @Parameter(
            names = ["-n", "--nodes"],
            description = "String of comma separated list of pubkey strings of the nodes that will be signers of the new blockchain",
            required = true)
    private var nodes = ""

    @Parameter(
            names = ["-bc", "--blockchain-config"],
            description = "Configuration file of blockchain (gtxml)",
            required = true)
    private var blockchainConfigFile = ""

    @Parameter(
            names = ["-fmt", "--format"],
            description = "format of blockchain configuration file (gtv|xml)",
            required = false)
    private var format : String? = null

    @Parameter(
            names = ["-c", "--container"],
            description = "which container bc should run in",
            required = true)
    private var container = ""

    override fun key() = "propose-blockchain"

    override fun execute(): CliResult {
        return try {
            CliExecutionE0(loadAppConfig()).proposeBlockchain(blockchainConfigFile, nodes, format, container)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}