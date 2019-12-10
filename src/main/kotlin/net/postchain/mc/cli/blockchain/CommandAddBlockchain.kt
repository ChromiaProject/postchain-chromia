package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.CliExecution
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok

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

    override fun key(): String = "add-blockchain"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addBlockchain(blockchainConfigFile, nodes)
            Ok("blockchain has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}