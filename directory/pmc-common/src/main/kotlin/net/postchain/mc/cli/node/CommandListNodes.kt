package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.cli.util.nodeConfigOption
import net.postchain.gtv.Gtv
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListNodes : CliktCommand(
    name = "list",
    help = "List all nodes"
) {
    private val nodeConfig by nodeConfigOption()

    private val showProvider by option("-p", "--provider", help = "Show provider information").flag()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val cliExecution = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig))
        val nodes: List<Gtv> = cliExecution.listNodesWithProvider()
        PrintUtils.printNodes(nodes, includeInactive, showProvider)
    }
}