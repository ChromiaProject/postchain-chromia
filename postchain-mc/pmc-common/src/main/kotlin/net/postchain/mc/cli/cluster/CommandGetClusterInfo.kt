package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusterData
import net.postchain.chain0.common.queries.getClusterNodes
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.mc.cli.util.*

class CommandGetClusterInfo : CliktCommand(
        name = "info",
        help = "Get information about a cluster"
) {

    private val client by clientOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    override fun run() {
        with (client.getClusterData(name)) {
            table {
                row("Name:", name)
                row("Governor:", governor)
                row("Is Operational:", isOperational.toString())
                row()
            }.render().also { println(it) }
            table {
                header("Provider", "Alias")
                client.getClusterProviders(name).forEach { provider ->
                    row(provider.pubkey.toString(), provider.name)
                }
                hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
            }.render().also { println(it) }
            table {
                header("Node", "Address")
                client.getClusterNodes(name).forEach { node ->
                    row(node.pubkey.toString(), "${node.host}:${node.port} / ${node.apiUrl}")
                }
                hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
            }
        }.render().also { println(it) }
    }
}
