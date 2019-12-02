package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add blockchain's signers")
class CommandAddBlockchainSigners: CommandBase() {

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

    override fun key(): String = "add-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            CliExecution(loadAppConfig()).addBlockchainSigners(blockchainRID, signers)
            Ok("Blockchain's signers have been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}