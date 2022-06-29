package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListBlockchainSigners : CliktCommand(
    name = "signers",
    help = "List blockchain signers"
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val listSigners = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listBlockchainSigners(blockchainRID.toHex())
        println("Signers:")
        PrintUtils.printBlockchainSigners(listSigners, includeInactive)
    }
}