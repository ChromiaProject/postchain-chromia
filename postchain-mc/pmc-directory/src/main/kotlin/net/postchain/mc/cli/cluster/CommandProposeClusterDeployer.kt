package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeClusterDeployer : CliktCommand(
    name = "deployer",
    help = "proposes an update of a cluster's deployer. New deployer must be an existing voter set."
) {
    private val config by configOption()

    private val deployer by nameOption("Name of new deployer").required()


    private val clusterName by option(
        "-c", "--cluster",
        help = "Name of existing cluster to update"
    ).required()

    override fun run() {
        CliExecution(config).proposeClusterDeployer(clusterName, deployer)
        println("proposal for deployer of cluster $clusterName has been added successfully")
    }
}
