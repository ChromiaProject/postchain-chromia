package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.configOption

class CommandUpdateProviderName : CliktCommand(
    name = "update",
    help = "Update the name of a provider"
) {
    private val config by configOption()

    private val pubkey by requiredPubkeyOption()

    private val name by option("-n", "--name", help = "Name of provider").default("")


    override fun run() {
        CliExecutionD1(config).updateProvider(pubkey, name)
        println("Provider has been renamed successfully")
    }

}