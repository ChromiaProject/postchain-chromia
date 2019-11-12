package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add blockchain configuration")
class CommandDeploy: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain ID",
            required = true)
    private var blockchainRID: String = ""

    @Parameter(
            names = ["-bc", "--blockchain-config"],
            description = "Configuration file of blockchain (gtxml or binary)",
            required = true)
    private var blockchainConfigFile = ""

    @Parameter(
            names = ["-h", "--height"],
            description = "Height of configuration",
            required = true)
    private var height = 0L

    override fun key(): String = "deploy"

    override fun execute(): CliResult {
        return try {
            CliExecution().addBlockchainConfiguration(config, blockchainRID, height, blockchainConfigFile, getSigner())
            Ok("Configuration has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}