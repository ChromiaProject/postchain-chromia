package net.postchain.mc.network

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class NetworkCommand : NoOpCliktCommand(
        help = "Commands relating to the network as a whole"
)

fun networkCommands() = NetworkCommand().subcommands(
        CommandInit(),
        SummaryCommand(),
        CommandVersion(),
        UpgradeCommand()
)
