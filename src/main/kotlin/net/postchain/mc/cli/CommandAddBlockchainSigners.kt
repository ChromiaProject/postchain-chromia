package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add blockchain's signers")
class CommandAddBlockchainSigners: CommandBase() {

    @Parameter(
            names = ["-bc", "--blockchain"],
            description = "Blockchain RID",
            required = true)
    private var blockchain = ""

    @Parameter(
            names = ["-s", "--signers"],
            description = "Blockchain's signers",
            required = true)
    private var signers = ""

    override fun key(): String = "add-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            CliExecution().addBlockchainSigners(loadAppConfig(), blockchain, signers)
            Ok("Blockchain's signers have been removed successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}