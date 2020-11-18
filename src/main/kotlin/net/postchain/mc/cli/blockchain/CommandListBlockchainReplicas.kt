package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List blockchain replicas. To see also inactive replicas, set flag -i.")
class CommandListBlockchainReplicas : CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain Rid",
            required = true)
    private var blockchainRID = ""

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include inactive replicas")
    private var includeInactive = false

    override fun key(): String = "list-blockchain-replicas"

    override fun execute(): CliResult {
        return try {
            val list = CliExecution(loadAppConfig()).listBlockchainReplicas(blockchainRID)
            println("Replicas:")
            PrintUtils.printBlockchainNodes(list, includeInactive)
            Ok("Listed blockchain replicas successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}