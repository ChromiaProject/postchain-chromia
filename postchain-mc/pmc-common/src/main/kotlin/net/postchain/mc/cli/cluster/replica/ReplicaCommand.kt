package net.postchain.mc.cli.cluster.replica

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class ReplicaCommand : NoOpCliktCommand("Cluster replica commands")

fun clusterReplicaCommands() = ReplicaCommand().subcommands(
        CommandAddClusterReplica(),
        CommandRemoveClusterReplica()
)