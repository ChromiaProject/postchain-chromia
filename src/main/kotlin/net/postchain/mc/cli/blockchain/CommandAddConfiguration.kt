package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "add new configuration to blockchain at specific height")
class CommandAddConfiguration: CommandBase() {

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

    override fun key() = "add-configuration"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).addConfiguration(blockchainRID, blockchainConfigFile, height, format)
            Ok("blockchain configuration has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}