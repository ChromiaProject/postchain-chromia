package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.PrintUtils
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandListProviders : CliktCommand(
    name = "list",
    help = "List all providers"
) {
    private val nodeConfig by nodeConfigOption()

    override fun run() {
        val providers = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).listProviders()
        PrintUtils.printProviders(providers)
    }
}
