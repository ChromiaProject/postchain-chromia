package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.Chromia0CliExecution

@Parameters(commandDescription = "add blockchain")
class CommandAddBlockchain: CommandBase() {

    @Parameter(
            names = ["-bc", "--blockchain-config"],
            description = "Configuration file of blockchain (gtxml)",
            required = true)
    private var blockchainConfigFile = ""

    @Parameter(
            names = ["-n", "--nodes"],
            description = "node list's pubkey",
            required = true)
    private var nodes = ""

    @Parameter(
            names = ["-fmt", "--format"],
            description = "format of blockchain configuration file (gtv|xml)",
            required = false)
    private var format : String? = null

    override fun key(): String = "add-blockchain"

    override fun execute(): CliResult {
        return try {
            Chromia0CliExecution(loadAppConfig()).addBlockchain(blockchainConfigFile, nodes, format)
            Ok("blockchain has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}