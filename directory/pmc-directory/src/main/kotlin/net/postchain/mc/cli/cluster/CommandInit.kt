package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandInit : CliktCommand(
    name = "initialize",
    help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val nodeConfig by nodeConfigOption()

    override fun run() {
        CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).init()
        println("You now have an initial provider that can vote for updates.")
    }
}