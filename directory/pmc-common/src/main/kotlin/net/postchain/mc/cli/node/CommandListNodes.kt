package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.gtv.Gtv
import net.postchain.mc.cli.util.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption

class CommandListNodes : CliktCommand(
    name = "list",
    help = "List all nodes"
) {
    private val config by configOption()

    private val showProvider by option("-p", "--provider", help = "Show provider information").flag()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val cliExecution = CliExecution(config)
        val nodes: List<Gtv> = cliExecution.listNodesWithProvider()
        PrintUtils.printNodes(nodes, includeInactive, showProvider)
    }
}