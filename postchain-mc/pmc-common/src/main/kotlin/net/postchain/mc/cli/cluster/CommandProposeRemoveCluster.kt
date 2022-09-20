package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeRemoveCluster : CliktCommand(
        name = "remove",
        help = "Propose removal of cluster. Command is irreversible"
) {
    private val config by configOption()

    private val name by nameOption("Cluster name").required()

    override fun run() {
        CliExecution(config).proposeRemoveCluster(name)
        println("Cluster has been proposed for removal")
    }
}