package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

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

    override fun key() = "add-configuration"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addConfiguration(blockchainRID, blockchainConfigFile, height)
            Ok("blockchain configuration has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}