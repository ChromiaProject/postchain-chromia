package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

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