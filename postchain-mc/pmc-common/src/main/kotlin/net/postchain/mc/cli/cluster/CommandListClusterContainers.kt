package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.ContainersPrinter
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandListClusterContainers : CliktCommand(
        name = "containers",
        help = "List all existing cluster containers"
) {
    private val config by configOption()
    private val clusterName by nameOption("Cluster name").required()

    override fun run() {
        val containers = CliExecution(config).listClusterContainers(clusterName)
        val res = ContainersPrinter.print(containers, false)
        println(res)
        println("Query returned successfully")
    }
}