package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List blockchain signers. To see also inactive signers, set flag -i.")
class CommandListBlockchainSigners : CommandBase() {


    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain Rid",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include inactive signers")
    private var includeInactive = false

    override fun key(): String = "blockchain-signers-list"

    override fun execute(): CliResult {
        return try {
            val listSigners = CliExecution(loadAppConfig()).listBlockchainSigners(blockchainRID)
            println("Signers:")
            PrintUtils.printBlockchainSigners(listSigners, includeInactive)
            Ok("Listed blockchain signers successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}