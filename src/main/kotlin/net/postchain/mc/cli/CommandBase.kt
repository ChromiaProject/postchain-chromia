package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import net.postchain.mc.config.app.AppConfig

abstract class CommandBase : Command {

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    protected fun loadAppConfig() = AppConfig.fromPropertiesFile(config)
}