package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getSummary
import net.postchain.mc.cli.util.clientOption

class SummaryCommand : CliktCommand(
        help = "Show summary of the network"
) {

    val client by clientOption()

    override fun run() {
        val summary = client.getSummary()
        println("Network summary:")
        table {
            row("Voter sets", summary.voterSets.toString())
            row("Providers", summary.providers.toString())
            row("Clusters", summary.clusters.toString())
            row("Containers", summary.containers.toString())
            row("Nodes", summary.nodes.toString())
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
                alignment(0, Table.Hints.Alignment.LEFT)
            }
        }
                .render(StringBuilder())
                .also { println(it) }
    }
}
