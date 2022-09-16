package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandProposeUnPauseBlockchain : CliktCommand(
    name = "start",
    help = "Propose starting a chain that has previously been stopped."
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        CliExecution(config).proposeUnPauseBlockchain(blockchainRID.toHex())
        println("Blockchain has been proposed for restart.")
    }
}