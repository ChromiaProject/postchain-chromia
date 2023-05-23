package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class NodeCommand : CliktCommand("Node commands") {
    override fun run() = Unit

    override fun aliases(): Map<String, List<String>> {
        return mapOf(
                "add" to listOf("register")
        )
    }
}

fun nodeCommands() = NodeCommand().subcommands(
        CommandRegisterNode(),
        CommandUpdateNode(),
        CommandReplaceNode(),
        CommandDisableNode(),
        CommandEnableNode(),
        CommandRemoveNode(),
        CommandGetNodeInfo(),
        CommandNodeVerify(),
        CommandListBlockchainsForNode(),
        CommandListContainersForNode(),
        CommandListNodes(),
)
