package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusterData
import net.postchain.chain0.common.queries.getClusterNodes
import net.postchain.chain0.common.queries.getClusterProviders
import net.postchain.chain0.model.ClusterResourceLimitType
import net.postchain.chain0.nm_api.nmGetClusterLimits
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
        }.render().also { println(it) }

        table {
            header("Resource type", "Value")
            val limits = client.nmGetClusterLimits(name)
            ClusterResourceLimitType.values().forEach {
                row(it.name, limits[it.name]?.toString() ?: "-1")
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { println(it) }
    }
}
