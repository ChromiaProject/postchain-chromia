package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import net.postchain.client.config.PostchainClientConfig
import net.postchain.mc.cli.config.PmcConfigProvider.envConfigurationFile
import net.postchain.mc.cli.config.PmcConfigProvider.globalConfigurationFile
import net.postchain.mc.cli.config.PmcConfigProvider.localConfigurationFile
fun CliktCommand.config() = option().convert { PostchainClientConfig.fromProperties(it) }.default(PmcConfigProvider.fromSystemConfig())

internal fun CliktCommand.pmcConfigFileOption() = option(help = "Configure pmc globally or locally to current folder")
    .switch(
        "--global" to globalConfigurationFile(),
        "--local" to localConfigurationFile(),
        "--env" to envConfigurationFile()
    ).default(localConfigurationFile())
