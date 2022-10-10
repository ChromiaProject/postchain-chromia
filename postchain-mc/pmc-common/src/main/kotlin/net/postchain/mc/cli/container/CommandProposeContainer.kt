package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOrGenerateOption

class CommandProposeContainer : CliktCommand(
    name = "add",
    help = "propose a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it."
) {
    private val config by lazy { read() }

    private val name by nameOrGenerateOption("Container name")

    private val clusterName by option(
        "-c", "--cluster",
        help = "Name of cluster to put container in. Must exist in database"
    ).required()

    private val deployerName by option(
        "-d", "--deployer",
        help = "Name of voter set authorized to operate in container."
    ).required()

    override fun run() {
        CliExecution(config).proposeContainer(name, clusterName, deployerName)
        println("proposal for container with name $name has been added successfully")
    }
}