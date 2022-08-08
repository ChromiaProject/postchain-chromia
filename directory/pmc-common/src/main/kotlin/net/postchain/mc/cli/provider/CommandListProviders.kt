package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.ProvidersPrinter
import net.postchain.mc.cli.util.configOption

class CommandListProviders : CliktCommand(
        name = "list",
        help = "List all providers"
) {
    private val config by configOption()

    override fun run() {
        val providers = CliExecution(config).listProviders()
        ProvidersPrinter.printProviders(providers)
    }
}
