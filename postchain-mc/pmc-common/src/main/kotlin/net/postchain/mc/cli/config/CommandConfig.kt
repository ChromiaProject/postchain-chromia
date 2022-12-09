package net.postchain.mc.cli.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.mc.cli.config.PmcConfigProvider.collectConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import java.awt.Desktop
import java.io.FileWriter


fun CliktCommand.configFileOption() = mutuallyExclusiveOptions(
        option("--global", help = "use global configuration file").flag().convert { PmcConfigProvider.globalConfigurationFile() },
        option("--local", help = "use project configuration file").flag().convert { PmcConfigProvider.localConfigurationFile() },
        option("--file", envvar = "POSTCHAIN_CLIENT_CONFIG", help = "use given configuration file (env: POSTCHAIN_CLIENT_CONFIG)")
                .file(mustExist = true, canBeDir = false),
        name = "Config file location",
).default(PmcConfigProvider.localConfigurationFile())

class CommandConfig : CliktCommand(
        name = "config",
        help = "Configure the management console"
) {

    private val configFile by configFileOption()

    private val get by option(help = "get value: name [value pattern]", metavar = "KEY")

    private val edit by option("-e", "--edit", help = "edit file using default editor").flag()

    private val list by option(help = "list all").flag()

    private val set by option("-s", "--set", help = "set values [key=value]", metavar = "KEY=VALUE").associate()

    override fun run() {
        if (list) {
            configFile.readLines()
                    .joinToString("\n") { if (it.startsWith("privkey")) "privkey=********************************" else it }
                    .also { return echo(it) }
        }
        if (edit) {
            if (!Desktop.isDesktopSupported()) throw IllegalArgumentException("Cannot edit file interactively, set parameters one by one")
            return Desktop.getDesktop().edit(configFile)
        }
        if (get != null) {
            if (get == "privkey") throw CliktError("Cannot print private key to stdout")
            return echo(collectConfiguration().getString(get))
        }
        val configuration = Parameters().properties()
                .setFile(configFile)
                .let {
                    FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                            .configure(it)
                            .configuration
                }
        if (set.isNotEmpty()) {
            set.forEach { (t, u) ->
                configuration.setProperty(t, u)
            }
            configuration.write(FileWriter(configFile))
        }
    }
}
