package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusterContainers
import net.postchain.chain0.common.queries.getClusterData
import net.postchain.chain0.common.queries.getClusterNodes
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.chain0.common.queries.getClusterReplicaNodes
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.validateAlphaNumeric

class CommandGetClusterInfo : CliktCommand(
        name = "info",
        help = "Get information about a cluster"
) {

    private val client by clientOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    override fun run() {
        val info = client.getClusterData(name)
        table {
            row("Name:", info.name)
            row("Governor:", info.governor)
            row("Is Operational:", info.isOperational.toString())
            info.clusterUnits?.let { row("Cluster Units:", it) }
            row()
        }.render().also { echo(it) }

        val clusterProviders = client.getClusterProviders(name)
        if (clusterProviders.isNotEmpty()) {
            table {
                header("Provider", "Alias")
                clusterProviders.forEach { provider ->
                    row(provider.pubkey.toString(), provider.name)
                }
                defaultHints()
            }.render().also { echo(it) }
        } else {
            echo("No providers")
        }

        val clusterNodes = client.getClusterNodes(name)
        if (clusterNodes.isNotEmpty()) {
            table {
                header("Node", "Address")
                clusterNodes.forEach { node ->
                    row(node.pubkey.toString(), "${node.host}:${node.port} / ${node.apiUrl}")
                }
                defaultHints()
            }.render().also { echo(it) }
        } else {
            echo("No nodes")
        }

        val clusterReplicas = client.getClusterReplicaNodes(name)
        if (clusterReplicas.isNotEmpty()) {
            table {
                header("Replica node", "Address")
                clusterReplicas.forEach { node ->
                    row(node.pubkey.toString(), "${node.host}:${node.port} / ${node.apiUrl}")
                }
                defaultHints()
            }.render().also { echo(it) }
        } else {
            echo("No replica nodes")
        }

        val containers = client.getClusterContainers(name)
        if (containers.isNotEmpty()) {
            table {
                header("Container", "Deployer")
                containers.forEach {
                    row(it.name, it.deployer)
                }
                defaultHints()
            }.render().also { echo(it) }
        } else {
            echo("No containers")
        }
    }

    private fun Table.defaultHints() {
        hints {
            borderStyle = Table.BorderStyle.SINGLE_LINE
            defaultAlignment = Table.Hints.Alignment.LEFT
        }
    }
}
