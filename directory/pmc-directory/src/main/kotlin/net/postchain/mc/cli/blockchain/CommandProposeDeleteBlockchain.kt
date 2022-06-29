package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeDeleteBlockchain : CliktCommand(
    name = "remove",
    help = "Propose removal of blockchain. Command is irreversible"
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeDeleteBlockchain(blockchainRID.toHex())
        println("Blockchain has been proposed for removal.")
    }
}