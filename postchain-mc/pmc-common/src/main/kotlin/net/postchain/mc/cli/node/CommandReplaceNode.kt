package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.replaceNodeOperation
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
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

    override fun run() {
        client.transactionBuilder()
            .replaceNodeOperation(client.config.pubkey().wData, old.wData, new.wData, host, port?.toLong(), apiUrl)
            .postSyncAwaitConfirmation()
            .printResult(
                "Node has been replaced",
                "Failed to replace node"
            )
    }
}
