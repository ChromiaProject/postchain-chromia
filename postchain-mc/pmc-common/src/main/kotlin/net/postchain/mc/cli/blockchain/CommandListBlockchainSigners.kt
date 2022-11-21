package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.queries.getBlockchainSigners
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.NodeListFormatter
import net.postchain.mc.cli.util.clientOption

class CommandListBlockchainSigners : CliktCommand(
        name = "signers",
        help = "List blockchain signers"
) {
    private val client by clientOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        NodeListFormatter.render(client.getBlockchainSigners(blockchainRID), includeInactive)
                .also { println(it) }
    }
}