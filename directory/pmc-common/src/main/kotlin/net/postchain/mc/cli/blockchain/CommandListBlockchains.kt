package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption

class CommandListBlockchains : CliktCommand(
    name = "list",
    help = "List blockchains"
) {
    private val config by configOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val listBlockchains = CliExecution(config).listBlockchains(includeInactive)
        listBlockchains.forEach { blockchain ->
            println(blockchain.toHex())
        }
    }
}