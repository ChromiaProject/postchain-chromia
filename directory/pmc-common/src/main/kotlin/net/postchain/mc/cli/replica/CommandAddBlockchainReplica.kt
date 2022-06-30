package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandAddBlockchainReplica : CliktCommand(
    name = "add",
    help = "add replica of a blockchain. The node is verifying but not building blocks."
) {

    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).addBlockchainReplica(blockchainRID.toHex(), key)
        println("Replica of blockchain has been added successfully")
    }

}