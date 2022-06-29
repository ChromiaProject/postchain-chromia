package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeContainer : CliktCommand(
    name = "add",
    help = "propose a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it."
) {
    private val nodeConfig by nodeConfigOption()

    private val name by nameOrGenerateOption("Container name")

    private val clusterName by option(
        "-c", "--cluster",
        help = "Name of cluster to put container in. Must exist in database"
    ).required()

    private val deployerName by option(
        "-d", "--deployer",
        "Name of voter set authorized to operate in container."
    ).required()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeContainer(name, clusterName, deployerName)
        println("proposal for container with name $name has been added successfully")
    }
}