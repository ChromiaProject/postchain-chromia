package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.*
import net.postchain.mc.cli.util.PrintUtils.printClusters

class CommandGetClusterInfo : CliktCommand(
        name = "info",
        help = "Get information about a cluster"
) {

    private val config by lazy { read() }

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
        val clusterInfo = CliExecution(config).getClusterInfo(name)
        if (clusterInfo != null) {
            printClusters(listOf(clusterInfo))

            val providers = CliExecution(config).getClusterProviders(name)
            println(ProvidersPrinter.printProvidersNamePubKey(providers))
            println()

            val nodes = CliExecution(config).getClusterNodes(name)
            println(NodesPrinter.printNodes(nodes))
            println()

            println("Query returned successfully")
        } else {
            println("Can't run query")
        }
    }
}