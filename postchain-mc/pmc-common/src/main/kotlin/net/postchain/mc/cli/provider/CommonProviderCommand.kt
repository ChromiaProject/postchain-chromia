package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class CommonProviderCommand : CliktCommand(
    name = "provider",
    help = "Provider commands"
) {
    override fun run() = Unit
}

fun commonProviderCommands() = CommonProviderCommand().subcommands(
    CommandGetProviderInfo(),
    CommandListProviderNodes(),
    CommandListProviders(),
    CommandRegisterProvider(),
    CommandUpdateProvider(),
)
