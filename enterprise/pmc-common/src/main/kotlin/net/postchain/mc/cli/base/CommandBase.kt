package net.postchain.mc.cli.base

import com.beust.jcommander.Parameter
import mu.KLogging
import net.postchain.client.config.PostchainClientConfig

abstract class CommandBase : Command {

    companion object : KLogging()

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    protected fun loadAppConfig(): PostchainClientConfig {
        try {
            return PostchainClientConfig.fromProperties(config)
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Cannot read config file or not found")
        }
    }
}