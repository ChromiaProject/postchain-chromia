package net.postchain.mc.cli.container.replica

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandAddContainerReplica : CliktCommand(
        name = "add",
        help = "add replica of a container to this cluster. (Another cluster is responsible for blockbuilding)"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(config).addContainerReplica(blockchainRID.toHex(), key)
        println("Replica of blockchain has been added successfully")
    }

}