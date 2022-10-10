package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption

class CommandGetNodeListVersion : CliktCommand(
    name = "version",
    help = "Node list version"
) {
    private val config by lazy { read() }

    override fun run() {
        val version = CliExecution(config).getNodeListVersion()
        println("version: $version")
    }
}