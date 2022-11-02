package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodesWithProvider
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption

class CommandListNodes : CliktCommand(
        name = "list",
        help = "List all nodes"
) {
    private val config by configOption()

    override fun run() {
        println("Nodes:")
        table {
            header("Pubkey", "Host", "Port", "Active", "Provided by")

            ClientUtil.fromConfig(config).getNodesWithProvider().forEach {
                row(it.pubkey.hex(), it.host, it.port.toString(), it.nodeActive.toString(), it.provider.toHex())
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { println(it) }
    }
}