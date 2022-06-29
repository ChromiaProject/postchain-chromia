package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class ProviderCommand: CliktCommand("Interact with providers") {
    override fun run() = Unit
}

fun providerCommands() = ProviderCommand().subcommands(
    CommandTransferActionPoints(),
    CommandUpdateProviderName(),
)