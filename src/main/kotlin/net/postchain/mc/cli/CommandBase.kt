package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import net.postchain.mc.config.app.AppConfig

abstract class CommandBase : Command {

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "blockchain rid",
            required = false)
    private var brid = ""

    protected fun loadAppConfig(): AppConfig {
        val config = AppConfig.fromPropertiesFile(config)

        if (config.brid.isEmpty()) {
            config.brid = brid
        }

        return config
    }
}