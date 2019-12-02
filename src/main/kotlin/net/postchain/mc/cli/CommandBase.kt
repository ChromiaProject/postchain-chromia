package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import mu.KLogging
import net.postchain.mc.config.app.AppConfig

abstract class CommandBase : Command {

    companion object : KLogging()

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    protected fun loadAppConfig(): AppConfig {
        try {
            return AppConfig.fromPropertiesFile(config)
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Cannot read config file or not found")
        }
    }
}