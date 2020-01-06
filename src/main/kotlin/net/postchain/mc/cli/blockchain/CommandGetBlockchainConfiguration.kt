package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "Get blockchain configuration")
class CommandGetBlockchainConfiguration : CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain rid",
            required = true)
    private var blockchainRID = ""


    @Parameter(
            names = ["-h", "--height"],
            description = "height of configuration")
    private var height = -1L

    override fun key(): String = "get-blockchain-configuration"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).getBlockchainConfiguration(blockchainRID, height)
            Ok("Get blockchain configuration successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
