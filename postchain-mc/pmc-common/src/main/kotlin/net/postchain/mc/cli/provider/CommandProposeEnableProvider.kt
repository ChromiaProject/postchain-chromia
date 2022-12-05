package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandProposeEnableProvider : CliktCommand(
    name = "enable",
    help = "Propose enabling an existing provider"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(config).proposeEnableProvider(key.hex())
        println("Proposal is registered.")
    }
}