package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class ContainerReplicaCommand : CliktCommand("Replica commands") {
    override fun run() = Unit
}

fun containerReplicaCommands() = ContainerReplicaCommand().subcommands(
    CommandAddContainerReplica(),
    CommandRemoveContainerReplica()
)
