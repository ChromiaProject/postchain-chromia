package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusterContainers
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.cli.util.nameOption

class CommandListClusterContainers : CliktCommand(
        name = "containers",
        help = "List all existing cluster containers"
) {
    private val client by clientOption()
    private val clusterName by nameOption("Cluster name").required()

    override fun run() {
        val containers = client.getClusterContainers(clusterName)
        if (containers.isEmpty()) {
            echo("No containers")
        } else {
            table {
                header("Container name", "deployer")
                containers.forEach {
                    row(it.name, it.deployer)
                }
                hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
            }.render().also { echo(it) }
        }
    }
}