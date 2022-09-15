package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.*
import net.postchain.mc.cli.util.PrintUtils.printClusters

class CommandGetClusterInfo : CliktCommand(
        name = "info",
        help = "Get information about a cluster"
) {

    private val config by configOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
        val clusterInfo = CliExecutionD1(config).getClusterInfo(name)
        if (clusterInfo != null) {
            printClusters(listOf(clusterInfo))

            val providers = CliExecutionD1(config).getClusterProviders(name)
            println(ProvidersPrinter.printProvidersNamePubKey(providers))
            println()

            val nodes = CliExecutionD1(config).getClusterNodes(name)
            println(NodesPrinter.printNodes(nodes))
            println()

            println("Query returned successfully")
        } else {
            println("Can't run query")
        }
    }
}