package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusterData
import net.postchain.chain0.common.queries.getClusterNodes
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.*
import net.postchain.mc.cli.util.PrintUtils.printClusters

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
                row("Deployer:", deployer)
                row("Is Operational:", isOperational.toString())

                row("")
                row("Providers:")
                client.getClusterProviders(name).forEach { provider ->
                    row(provider.name, provider.pubkey.toString())
                }
                row("")
                row("Nodes:")
                client.getClusterNodes(name).forEach { node ->
                    row(node.pubkey.toString(), "${node.host}:${node.port} / ${node.apiUrl}")
                }
                hints { defaultAlignment = Table.Hints.Alignment.LEFT }
            }
        }.render().also { println(it) }
    }
}
