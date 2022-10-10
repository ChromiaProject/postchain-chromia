package net.postchain.mc.cli.container.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption

class CommandRemoveContainerReplica : CliktCommand(
    name = "remove",
    help = "remove replica of this container from this cluster"
) {
    private val config by lazy { read() }

    private val clusterName by option("-cl", "--cluster", help = "Cluster name").required()

    private val containerName by option("-co", "--container", help = "Container name").required()

    override fun run() {
        CliExecution(config).removeContainerReplica(clusterName, containerName)
        println("Container replica has been removed from cluster successfully")
    }

}