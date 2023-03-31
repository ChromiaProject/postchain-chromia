package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.nopClientOption

class CommandDisableNode : CliktCommand(
        name = "disable",
        help = "Disables node and removes it from clusters, cluster replicas, blockchain replicas"
) {
    private val client by nopClientOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        client.transactionBuilder()
                .disableNodeOperation(client.config.pubkey().data, key.data)
                .postAwaitConfirmation()
                .printResult(
                        "Node disabled",
                        "Cannot disable node"
                )
    }
}