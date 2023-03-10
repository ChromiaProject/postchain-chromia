package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.queries.getBlockchainReplicas
import net.postchain.mc.cli.blockchainRidOption
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.NodeListFormatter.renderNodes
import net.postchain.mc.cli.util.clientOption

class CommandListBlockchainReplicas : CliktCommand(
        name = "replicas",
        help = "List blockchain replicas"
) {
    private val client by clientOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val replicas = client.getBlockchainReplicas(blockchainRID)
        if (replicas.isEmpty()) {
            echo("No replicas")
        } else {
            renderNodes(replicas, includeInactive).also { echo(it) }
        }
    }
}