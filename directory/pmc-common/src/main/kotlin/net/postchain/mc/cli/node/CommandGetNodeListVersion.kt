package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandGetNodeListVersion : CliktCommand(
    name = "version",
    help = "Node list version"
) {
    private val nodeConfig by nodeConfigOption()

    override fun run() {
        val version = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).getNodeListVersion()
        println("version: $version")
    }
}