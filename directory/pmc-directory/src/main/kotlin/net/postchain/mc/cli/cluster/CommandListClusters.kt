package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandListClusters : CliktCommand(
    name = "list",
    help = "List all existing clusters"
) {
    private val nodeConfig by nodeConfigOption()
    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
            val clusters = CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).listClusters()
            clusters.forEach {
                println(it.asString())
            }
            println("Query returned successfully")
    }
}