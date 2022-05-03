package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "propose a new blockchain in a specific container. Change will be applied after voting within the deployer voter set of the cluster that the container belongs to.")
class CommandProposeBlockchain: CommandBase() {

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
            CliExecutionD1(loadAppConfig()).proposeBlockchain(blockchainConfigFile, format, container)
            Ok("Proposal is registered.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}