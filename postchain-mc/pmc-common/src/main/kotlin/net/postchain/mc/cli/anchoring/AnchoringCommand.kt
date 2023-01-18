package net.postchain.mc.cli.anchoring

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class AnchoringCommand : CliktCommand("Interactions with anchoring chain configuration") {
    override fun run() = Unit
}

fun anchoringCommands() = AnchoringCommand().subcommands(
    CommandProposeAnchoringConfiguration(),
    CommandGetAnchoringConfiguration()
)
