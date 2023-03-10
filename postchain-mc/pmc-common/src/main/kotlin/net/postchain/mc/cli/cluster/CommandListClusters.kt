package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusters
import net.postchain.mc.cli.util.clientOption

class CommandListClusters : CliktCommand(
        name = "list",
        help = "List all existing clusters"
) {
    private val client by clientOption()

    override fun run() {
        val clusters = client.getClusters()
        if (clusters.isEmpty()) {
            echo("No clusters")
        } else {
            table {
                header("Name", "Governor", "Operational")
                clusters.forEach {
                    row(it.name, it.governor, it.operational.toString())
                }
                hints {
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                }
            }.render().also { println(it) }
        }
    }
}
