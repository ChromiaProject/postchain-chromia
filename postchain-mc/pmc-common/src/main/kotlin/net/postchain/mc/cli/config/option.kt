package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import net.postchain.mc.cli.config.PmcConfigProvider.envConfigurationFile
import net.postchain.mc.cli.config.PmcConfigProvider.globalConfigurationFile
import net.postchain.mc.cli.config.PmcConfigProvider.localConfigurationFile

internal fun CliktCommand.pmcConfigFileOption() = option(help = "Configure pmc globally or locally to current folder")
    .switch(
        "--global" to globalConfigurationFile(),
        "--local" to localConfigurationFile(),
        "--env" to envConfigurationFile()
    ).default(localConfigurationFile())
