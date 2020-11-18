package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
//import net.postchain.mc.cli.chromia0.CliExecutionC0
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "list all active nodes. To see also the corresponding providers' information set flag " +
        "-p. To see also inactive nodes, set flag -i.")
class CommandListNodes : CommandBase() {

    @Parameter(
            names = ["-p", "--provider"],
            description = "Show provider information")
    private var showProvider = false

    @Parameter(
            names = ["-i", "--includeinactive"],
            description = "Include inactive nodes")
    private var includeInactive = false

    override fun key(): String = "list-nodes"

    override fun execute(): CliResult {
        return try {
            val cliExecution = CliExecution(loadAppConfig())
            val nodes : List<Gtv>
                nodes = cliExecution.listNodesWithProvider()
                PrintUtils.printNodes(nodes, includeInactive, showProvider)
            Ok("Listed nodes successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}