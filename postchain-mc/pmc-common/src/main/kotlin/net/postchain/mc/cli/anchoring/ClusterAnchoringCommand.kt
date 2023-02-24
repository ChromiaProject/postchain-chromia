package net.postchain.mc.cli.anchoring

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class ClusterAnchoringCommand : CliktCommand("Interactions with cluster anchoring chain configuration") {
    override fun run() = Unit
}

fun clusterAnchoringCommands() = ClusterAnchoringCommand().subcommands(
    CommandProposeClusterAnchoringConfiguration(),
    CommandGetClusterAnchoringConfiguration()
)
