package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "remove blockchain's signers")
class CommandRemoveBlockchainSigners: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain RID",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-s", "--signers"],
            description = "Blockchain's signers",
            required = true)
    private var signers = ""

    override fun key(): String = "remove-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            CliExecution().removeBlockchainSigners(loadAppConfig(), blockchainRID, signers)
            Ok("Blockchain's signers have been removed successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}