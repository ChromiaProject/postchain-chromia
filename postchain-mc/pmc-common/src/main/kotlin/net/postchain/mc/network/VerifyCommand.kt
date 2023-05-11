package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common.queries.getAllNodes
import net.postchain.common.BlockchainRid
import net.postchain.mc.cli.util.clientOption

class VerifyCommand : CliktCommand(help = "Verify that all nodes are accessible") {
    val client by clientOption()

    private val showProgress by option(help = "Show which node is currently being verified").flag()

    override fun run() {
        client.requireApiVersion(2)
        val nodeVerifier = NodeVerifier(client.config, client.cmGetSystemAnchoringChain()?.let { BlockchainRid(it) })
        table {
            header("Node", "Network", "Api", "Management chain", "System anchoring")

            client.getAllNodes(false).forEach { node ->
                if (showProgress) echo("Verifying node: ${node.info.apiUrl}, ${node.info.pubkey}")
                val (apiAccessible, height, sacHeight) = nodeVerifier.verifyApi(node.info)
                val hostResponds = nodeVerifier.verifyHost(node.info)
                row(
                        node.info.pubkey.toHex(),
                        "${hostResponds.isOk()}",
                        "${apiAccessible.isOk()}",
                        "$height",
                        "$sacHeight"
                )
                hints {
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                    defaultAlignment = Table.Hints.Alignment.LEFT
                }
            }
        }.render().also { println(it) }
    }

    private fun Boolean?.isOk(): String = this?.let { if (this) "OK" else "Bad" } ?: "Bad"

}
