package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeBlockchain: CliktCommand(
    name = "add",
    help = "propose a new blockchain in a specific container. Change will be applied after voting within the deployer voter set of the cluster that the container belongs to."
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainConfigFile by blockchainConfigOption()

    private val container by option("-c", "--container", help = "Name of container to run in").required()

    override fun run() {
            CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeBlockchain(blockchainConfigFile, null, container)
            println("Blockchain has been proposed")
    }
}