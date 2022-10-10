package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption

class CommandListBlockchainsForNode : CliktCommand(
    name = "blockchains",
    help = "List blockchains for node"
) {
    private val config by lazy { read() }

    private val key by requiredPubkeyOption()

    override fun run() {
        val listBlockchains = CliExecution(config).listBlockchainsForNode(key)
        listBlockchains.forEach { blockchain ->
            println(blockchain.toHex())
        }
    }
}