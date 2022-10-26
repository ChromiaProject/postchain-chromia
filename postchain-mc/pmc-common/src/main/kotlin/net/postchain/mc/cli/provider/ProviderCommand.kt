package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.subcommands

fun providerCommands() = commonProviderCommands().subcommands(
        CommandProposeEnableProvider(),
        CommandProposeDisableProvider(),
        CommandTransferActionPoints(),
)