package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandRemoveNode : CliktCommand(
    name = "remove",
    help = "Inactivate node"
) {
    private val nodeConfig by nodeConfigOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).removeNode(key)
        println("Node has been removed successfully")
    }

}