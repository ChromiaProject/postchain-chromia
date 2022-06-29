package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandListBlockchainsForNode : CliktCommand(
    name = "blockchains",
    help = "List blockchains for node"
) {
    private val nodeConfig by nodeConfigOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val listBlockchains = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listBlockchainsForNode(key)
        listBlockchains.forEach { blockchain ->
            println(blockchain.toHex())
        }
    }
}