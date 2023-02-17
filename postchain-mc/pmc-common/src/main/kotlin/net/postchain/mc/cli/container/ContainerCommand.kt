package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class ContainerCommand : CliktCommand("Container commands") {
    override fun run() = Unit
}

fun containerCommands() = ContainerCommand().subcommands(
        CommandProposeContainer(),
        CommandGetContainerInfo(),
        CommandProposeContainerResourceLimits(),
        CommandListContainers(),
        CommandProposeRemoveContainer()
)