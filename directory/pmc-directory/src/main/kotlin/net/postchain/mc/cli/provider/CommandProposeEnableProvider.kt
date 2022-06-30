package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.configOption

class CommandProposeEnableProvider : CliktCommand(
    name = "enable",
    help = "Propose enabling an existing provider"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecutionD1(config).proposeEnableProvider(key)
        println("Proposal is registered.")
    }
}