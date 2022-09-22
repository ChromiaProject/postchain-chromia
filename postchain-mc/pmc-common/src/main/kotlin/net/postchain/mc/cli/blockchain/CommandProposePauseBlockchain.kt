package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandProposePauseBlockchain : CliktCommand(
        name = "stop",
        help = "Propose stopping a blockchain from building blocks"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        CliExecution(config).proposePauseBlockchain(blockchainRID.toHex())
        println("Blockchain has been proposed to stop")

    }
}