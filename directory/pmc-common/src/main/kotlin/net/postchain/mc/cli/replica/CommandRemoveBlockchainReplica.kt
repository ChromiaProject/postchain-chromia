package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandRemoveBlockchainReplica : CliktCommand(
    name = "remove",
    help = "remove replica of a blockchain"
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig))
            .removeBlockchainReplica(blockchainRID.toHex(), key)
        println("Replica node has been removed successfully")
    }

}