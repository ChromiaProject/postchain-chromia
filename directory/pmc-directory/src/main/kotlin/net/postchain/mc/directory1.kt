package net.postchain.mc

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.cli.*
import net.postchain.mc.cli.cluster.*

class ManagementConsole : CliktCommand(name = "postchain-mc") {
    override fun run() = Unit
}

fun main(args: Array<String>) = ManagementConsole()
    .subcommands(
        CommandKeygen(),

        // Init
        CommandInit(),

        // Cluster
        clusterCommand(),
        )
    .main(args)
