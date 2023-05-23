package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.operations.replaceNodeOperation
import net.postchain.chain0.common.operations.replaceNodeWithUnitsOperation
import net.postchain.chain0.version.apiVersion
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.hostOption
import net.postchain.mc.cli.portOption
import net.postchain.mc.cli.util.clusterUnitsOption
import net.postchain.mc.cli.util.nopClientOption

class CommandReplaceNode : CliktCommand(
        name = "replace",
        help = """
        Replace a node with a new one (Used to rotate keypairs). Add the keys to the nodes to the client configuration as comma-delimited list:
        pubkey=<key>,<old-node-key>,<new-node-key>
        privkey=<key>,<old-node-key>,<new-node-key>
    """.trimIndent()
) {
    private val client by nopClientOption()

    private val old by option("--old-key", help = "Public key of the node to replace").convert { PubKey(it) }.required()
    private val new by option("--new-key", help = "Public key of the new node").convert { PubKey(it) }.required()

    private val host by hostOption()

    private val port by portOption()

    private val apiUrl by option("-a", "--api-url", help = "api url")

    private val clusterUnits by clusterUnitsOption().default(1)

    override fun run() {
        val apiVersion = client.apiVersion()
        client.transactionBuilder()
                .apply {
                    when {
                        apiVersion >= 3 -> replaceNodeWithUnitsOperation(client.config.pubkey().data, old.data, new.data, host, port?.toLong(), apiUrl, clusterUnits)
                        else -> replaceNodeOperation(client.config.pubkey().data, old.data, new.data, host, port?.toLong(), apiUrl)
                    }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Node has been replaced",
                        "Failed to replace node"
                )
    }
}
