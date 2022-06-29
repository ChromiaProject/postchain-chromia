package net.postchain.mc.cli.container.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandRemoveContainerReplica : CliktCommand(
    name = "remove",
    help = "remove replica of this container from this cluster"
) {
    private val nodeConfig by nodeConfigOption()

    private val clusterName by option("-cl", "--cluster", help = "Cluster name").required()

    private val containerName by option("-co", "--container", help = "Container name").required()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).removeContainerReplica(clusterName, containerName)
        println("Container replica has been removed from cluster successfully")
    }

}