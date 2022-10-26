package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import de.m3y.kformat.table
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
        table {
        CliExecution(config).listClusterContainers(clusterName).forEach {
            row(it.name, it.deployer)
        }
        }.render().also { println(it) }
    }
}