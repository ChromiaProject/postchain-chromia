package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.PrintUtils.printClusters
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.validateAlphaNumeric
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandGetClusterInfo : CliktCommand(
    name = "info",
    help = "Get information about a cluster"
) {

    private val nodeConfig by nodeConfigOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
            val clusterInfo = CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).getClusterInfo(name)
            printClusters(listOf(clusterInfo))
            println("Query returned successfully")
    }
}