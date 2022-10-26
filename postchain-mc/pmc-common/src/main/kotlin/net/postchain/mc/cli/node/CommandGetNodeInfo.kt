package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.listClustersOfNode
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandGetNodeInfo : CliktCommand(
        name = "info",
        help = "Get node info for given node pubkey"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val node = client.getNodeData(PubKey(key))
        println("Active: ${node.active}")
        println("Host: ${node.host}")
        println("Port: ${node.port}")
        println("Provided by: ${node.provider.toHex()}")
        val clusters = client.listClustersOfNode(node.pubkey)
        println("Used by clusters: $clusters")
    }
}
