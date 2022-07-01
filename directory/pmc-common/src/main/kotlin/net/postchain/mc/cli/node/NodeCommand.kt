package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class NodeCommand : CliktCommand("Node commands") {
    override fun run() = Unit
}

fun nodeCommands() = NodeCommand().subcommands(
    CommandAddNode(),
    CommandGetNodeInfo(),
    CommandGetNodeListVersion(),
    CommandListBlockchainsForNode(),
    CommandListNodes(),
    CommandRemoveNode()
)
