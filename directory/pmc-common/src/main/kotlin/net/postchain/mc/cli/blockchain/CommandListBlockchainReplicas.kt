package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListBlockchainReplicas : CliktCommand(
    name = "replicas",
    help = "List blockchain replicas"
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val list = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listBlockchainReplicas(blockchainRID.toHex())
        println("Replicas:")
        PrintUtils.printBlockchainReplicas(list, includeInactive)
    }
}