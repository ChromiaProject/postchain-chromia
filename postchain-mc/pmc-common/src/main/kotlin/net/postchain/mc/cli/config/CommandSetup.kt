package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.blockchainRidOption
import net.postchain.client.config.PostchainClientConfig

class CommandSetup : CliktCommand(
    name = "setup",
    help = "Create new provider configuration"
) {

    private val brid by blockchainRidOption()

    private val configFile by pmcConfigFileOption()

    override fun run() {
        if (configFile.exists()) throw IllegalArgumentException("Configuration file ${configFile.absolutePath} already exists.")

        val config = PmcConfigProvider.createConfigFile(configFile, brid)
        validateConfig(config)
        println("Configuration file ${configFile.absolutePath} created")
    }

    private fun validateConfig(config: PostchainClientConfig) {
        if (config.signers.isEmpty()) println("Provider pubkey/private key missing")
        if (config.endpointPool.size() == 0) println("api.url must contain at least one endpoint")
    }
}
