package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandRegisterProvider : CliktCommand(
    name = "add",
    help = "register new provider with given pubkey. default tier = 0. Tier-0 providers are enabled automatically, higher order tiers need voting to be enabled."
) {
    private val config by configOption()
    private val key by requiredPubkeyOption()

    private val nodeProvider by option("-np", "--node-provider", help = "If this provider should be able to add signer nodes").flag()

    override fun run() {
        CliExecution(config).registerProvider(key, nodeProvider)
        println("Provider has been registered successfully")
    }
}