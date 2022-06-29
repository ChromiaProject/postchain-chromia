package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListBlockchains : CliktCommand(
    name = "list",
    help = "List blockchains"
) {
    private val nodeConfig by nodeConfigOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val listBlockchains = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listBlockchains(includeInactive)
        listBlockchains.forEach { blockchain ->
            println(blockchain.toHex())
        }
    }
}