package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
//import net.postchain.mc.cli.chromia0.CliExecutionC0

// TODO: [et][merge]: Remove it?

/*
@Parameters(commandDescription = "add blockchain's signers")
class CommandAddBlockchainSigners : CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true
    )
    private var blockchainRID = ""

    @Parameter(
            names = ["-hd", "--height-delay"],
            description = "height delay at which new configuration will be applied (5 by default)",
            required = false
    )
    private var heightDelay: Long = -1L

    @Parameter(
            names = ["-s", "--signers"],
            description = "Blockchain's signers",
            required = true
    )
    private var signers = ""

    override fun key(): String = "add-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).addBlockchainSigners(blockchainRID, signers, heightDelay)
            Ok("Blockchain's signers have been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
*/

