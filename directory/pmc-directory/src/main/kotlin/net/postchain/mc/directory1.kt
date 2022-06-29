package net.postchain.mc

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.cli.*
import net.postchain.mc.cli.cluster.CommandAddCluster
import net.postchain.mc.cli.cluster.CommandGetClusterInfo
import net.postchain.mc.cli.cluster.CommandInit
import net.postchain.mc.cli.cluster.CommandListClusters

class ManagementConsole : CliktCommand(name = "postchain-mc") {
    override fun run() = Unit
}

fun main(args: Array<String>) = ManagementConsole()
    .subcommands(
        CommandKeygen(),

        // Init
        CommandInit(),

        // Cluster
        CommandAddCluster(),
        CommandGetClusterInfo(),
        CommandListClusters(),
        )
    .main(args)
