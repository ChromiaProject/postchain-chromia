package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeDisableProvider : CliktCommand(
    name = "disable",
    help = "Propose disabling an existing provider"
) {
    private val nodeConfig by nodeConfigOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeDisableProvider(key)
        println("Proposal is registered.")
    }
}
