package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListProviderNodes : CliktCommand(
    name = "nodes",
    help = "List nodes by provider"
) {
    private val nodeConfig by nodeConfigOption()

    private val key by requiredPubkeyOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val nodes = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listNodesByProvider(key)
        PrintUtils.printNodes(nodes, includeInactive, false)
    }
}