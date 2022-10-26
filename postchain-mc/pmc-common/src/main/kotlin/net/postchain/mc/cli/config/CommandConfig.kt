package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import java.awt.Desktop


class CommandConfig : CliktCommand(
        name = "config",
        help = "Configure the management console"
) {

    private val show by option(help = "Show current configuration").flag()

    private val config by pmcConfigFileOption()

    override fun run() {
        if (show) {
            config.readLines()
                    .joinToString("\n") { if (it.startsWith("privkey")) "privkey=********************************" else it }
                    .also { println(it) }
            return
        }
        if (!config.exists()) PmcConfigProvider.createConfigFile(config, null) else Desktop.getDesktop().edit(config)
    }
}
