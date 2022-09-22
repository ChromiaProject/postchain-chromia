package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandProposeDeleteBlockchain : CliktCommand(
        name = "remove",
        help = "Propose removal of blockchain. Command is irreversible"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        CliExecution(config).proposeDeleteBlockchain(blockchainRID.toHex())
        println("Blockchain has been proposed for removal.")
    }
}