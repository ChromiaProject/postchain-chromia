package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandProposeDisableProvider : CliktCommand(
        name = "disable",
        help = "Propose disabling an existing provider"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        CliExecution(config).proposeDisableProvider(key.hex())
    }
}
