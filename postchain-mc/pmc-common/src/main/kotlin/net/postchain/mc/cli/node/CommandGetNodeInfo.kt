package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.listClustersOfNode
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.configOption

class CommandGetNodeInfo : CliktCommand(
        name = "info",
        help = "Get node info for given node pubkey"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val node = client.getNodeData(key)
        table {
            row("Active:", "${node.active}")
            row("Host:", node.host)
            row("Port:", "${node.port}")
            row("REST API:", node.apiUrl)
            row("Provided by:", node.provider.toHex())
            node.clusterUnits?.let { row("Cluster Units:", it.toString()) }
            val clusters = client.listClustersOfNode(PubKey(node.pubkey))
            row("Used by clusters:", "$clusters")
            hints { defaultAlignment = Table.Hints.Alignment.LEFT }
        }.render().also { echo(it) }
    }
}
