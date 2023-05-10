package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common.queries.getAllNodes
import net.postchain.common.BlockchainRid
import net.postchain.mc.cli.util.clientOption

class VerifyCommand : CliktCommand(help = "Verify that all nodes are accessible") {
    val client by clientOption()

    override fun run() {
        client.requireApiVersion(2)
        val nodeVerifier = NodeVerifier(client.config, client.cmGetSystemAnchoringChain()?.let { BlockchainRid(it) })
        table {
            header("Node", "Network address ok", "Api accessible", "Management chain Height", "System anchoring chain height")
            client.getAllNodes(false).forEach { node ->
                val (apiAccessible, height, sacHeight) = nodeVerifier.verifyApi(node.info)
                row(
                        node.info.pubkey.toHex(),
                        nodeVerifier.verifyHost(node.info).toString(),
                        apiAccessible.toString(),
                        height?.toString() ?: "",
                        sacHeight?.toString() ?: ""
                )
                hints {
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                    defaultAlignment = Table.Hints.Alignment.LEFT
                }
            }
        }.render().also { println(it) }
    }
}
