package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodeContainers
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.clientOption

class CommandListContainersForNode : CliktCommand(
        name = "containers",
        help = "List containers for node"
) {
    private val client by clientOption()
    private val key by requiredPubkeyOption()

    override fun run() {
        val containers = client.getNodeContainers(key)
        if (containers.isEmpty()) {
            echo("No containers")
        } else {
            table {
                header("Name", "Cluster", "Deployer")
                containers.forEach {
                    row(it.name, it.cluster, it.deployer)
                }
                hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
            }.render().also { echo(it) }
        }
    }
}