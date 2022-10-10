package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.*
import net.postchain.client.config.PostchainClientConfig
import net.postchain.mc.cli.config.PmcConfigProvider.globalConfigurationFile
import net.postchain.mc.cli.config.PmcConfigProvider.localConfigurationFile
import java.awt.Desktop
import java.io.File


class CommandConfig : CliktCommand(
    name = "config",
    help = "Configure the management console"
) {

    private val show by option(help = "Show current configuration").flag()

    private val config by option(help = "Configure pmc globally or locally to current folder")
        .switch(
            "--global" to globalConfigurationFile(),
            "--local" to localConfigurationFile()
        ).required()

    override fun run() {
        if (show) {
            config.readLines()
                .joinToString("\n") { if (it.startsWith("privkey")) "privkey=**********" else it }
                .also { println(it) }
            return
        }
        if (!config.exists()) PmcConfigProvider.createConfigFile(config, null) else Desktop.getDesktop().edit(config)
    }
}
