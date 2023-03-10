package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodesWithProvider
import net.postchain.mc.cli.util.clientOption

class CommandListNodes : CliktCommand(
        name = "list",
        help = "List all nodes"
) {
    private val client by clientOption()

    override fun run() {
        val nodes = client.getNodesWithProvider()
        if (nodes.isEmpty()) {
            echo("No nodes")
        } else {
            table {
                header("Pubkey", "Host", "Port", "Active", "Provided by")
                nodes.forEach {
                    row(it.pubkey.toHex(), it.host, it.port.toString(), it.nodeActive.toString(), it.provider.toHex())
                }
                hints {
                    defaultAlignment = Table.Hints.Alignment.LEFT
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                }
            }.render().also { echo(it) }
        }
    }
}