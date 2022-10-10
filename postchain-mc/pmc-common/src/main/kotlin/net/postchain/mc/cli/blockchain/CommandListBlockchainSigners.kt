package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.util.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption

class CommandListBlockchainSigners : CliktCommand(
    name = "signers",
    help = "List blockchain signers"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val listSigners = CliExecution(config).listBlockchainSigners(blockchainRID.toHex())
        println("Signers:")
        PrintUtils.printBlockchainSigners(listSigners, includeInactive)
    }
}