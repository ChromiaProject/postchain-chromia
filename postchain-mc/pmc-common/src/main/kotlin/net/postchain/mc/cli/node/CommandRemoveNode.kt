package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandRemoveNode : CliktCommand(
        name = "remove",
        help = "Inactivate node"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(config).removeNode(key)
        println("Node has been removed successfully")
    }

}