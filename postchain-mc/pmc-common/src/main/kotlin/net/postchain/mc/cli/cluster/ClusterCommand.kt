package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.mc.cli.cluster.replica.clusterReplicaCommands

class ClusterCommand : CliktCommand("Interacting with clusters") {
    override fun run() = Unit
}

fun clusterCommands() = ClusterCommand().subcommands(
        CommandListClusters(),
        CommandAddCluster(),
        CommandGetClusterInfo(),
        CommandListClusterContainers(),
        CommandProposeClusterProvider(),
        CommandProposeClusterResourceLimits(),
        CommandProposeRemoveCluster(),
        clusterReplicaCommands()
)