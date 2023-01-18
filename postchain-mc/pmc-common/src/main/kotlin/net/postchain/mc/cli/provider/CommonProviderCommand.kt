package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class CommonProviderCommand : CliktCommand(
        name = "provider",
        help = "Provider commands"
) {
    override fun run() = Unit

    override fun aliases(): Map<String, List<String>> {
        return mapOf(
                "add" to listOf("register")
        )
    }
}

fun providerCommands() = CommonProviderCommand().subcommands(
        CommandGetProviderInfo(),
        CommandListProviderQuotas(),
        CommandProposeProviderQuota(),
        CommandListProviderNodes(),
        CommandListProviders(),
        CommandRegisterProvider(),
        CommandProposeEnableProvider(),
        CommandPromoteProvider(),
        CommandProposeDisableProvider(),
        CommandTransferActionPoints(),
)
