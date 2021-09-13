package net.postchain.mc.cli.votingupdates

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.directory1.CliExecutionD1

@Parameters(commandDescription = "propose new configuration to blockchain at specific height. Height must be > current height " +
        " and > all previously approved configuration heights. " +
        "Use force flag -f to override previously added configs or to squeeze in a configuration at a height < previously approved config heights." +
        "Change will be applied after voting.")
class CommandProposeConfiguration: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-bc", "--blockchain-config"],
            description = "Configuration file of blockchain (gtxml)",
            required = true)
    private var blockchainConfigFile = ""

    @Parameter(
            names = ["-h", "--height"],
            description = "block height at which new configuration will be applied",
            required = true)
    private var height = 0L

    @Parameter(
            names = ["-fmt", "--format"],
            description = "format of blockchain configuration file (gtv|xml)",
            required = false)
    private var format : String? = null

    @Parameter(
            names = ["-f", "--force"],
            description = "Force the addition of blockchain configuration " +
                    "for a height that already exists or a height < already proposed configuration heights.")
    private var force = false

    override fun key() = "propose-configuration"

    override fun execute(): CliResult {
        return try {
            CliExecutionD1(loadAppConfig()).proposeConfiguration(blockchainRID, blockchainConfigFile, height, format, force)
            Ok("Proposal is registered. Now waiting for approval.")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}