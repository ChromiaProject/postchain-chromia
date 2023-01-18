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
        table {
            header("Name", "Cluster", "Deployer")
            client.getNodeContainers(key).forEach {
                row(it.name, it.cluster, it.deployer)
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { println(it) }
    }
}