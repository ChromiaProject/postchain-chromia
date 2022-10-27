package net.postchain.deployment.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.common.hexStringToByteArray
import net.postchain.deployment.ChromiaDeploymentTool
import net.postchain.deployment.DeploymentTool
import net.postchain.gtv.GtvEncoder
import kotlin.io.path.absolutePathString

abstract class DeployXmlCommand(name: String, help: String) : CliktCommand(name = name, help = help) {
    val clientConfig by clientConfigOption()

    val sourceDir by option("-d", "--source-dir", help = "Rell source dir").path(mustExist = true, canBeDir = true, canBeFile = false).required()

    val outputDir by option("-o", "--output-dir", help = "Generated configuration output dir").path(mustExist = false, canBeDir = true, canBeFile = false).required()

    val deployXmlFile by argument(name = "deploy.xml").path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)
}

class ContainerCommand : CliktCommand(name = "container", help = "Create container") {
    private val clientConfig by clientConfigOption()

    private val containerName by option("-c", "--container", help = "Container name").required()

    private val clusterName by option("--cluster", help = "Cluster name").required()

    private val proofHex by option("--proof", metavar = "HEX", help = "Hex encoded staking proof").required()

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(clientConfig?.absolutePathString())

        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        deploymentTool.createContainer(
                clientConfig,
                containerName = containerName,
                clusterName = clusterName,
                proofHex.hexStringToByteArray()
        )
    }
}

class DeployCommand : DeployXmlCommand(name = "deploy", help = "Deploy blockchain into container") {
    private val containerName by option("-c", "--container", help = "Container name").required()

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(clientConfig?.absolutePathString())

        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        val blockchainConfigurations = deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
        deploymentTool.deployBlockchain(
                clientConfig,
                blockchainConfigurations.name,
                containerName,
                GtvEncoder.encodeGtv(blockchainConfigurations.configurations.first().second)
        )
    }
}

class UpdateCommand : DeployXmlCommand(name = "update", help = "Update configuration of running blockchain") {
    private val configVersion by option("-v", "--version", help = "Configuration version to use").long()

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(clientConfig?.absolutePathString())

        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        val blockchainConfigurations = deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
        val configuration = if (configVersion != null) {
            blockchainConfigurations.configurations.find { it.first == configVersion }?.second
                    ?: throw CliktError("configuration version $configVersion not found")
        } else {
            blockchainConfigurations.configurations.last().second
        }
        deploymentTool.updateBlockchain(
                clientConfig,
                blockchainConfigurations.blockchainRid,
                GtvEncoder.encodeGtv(configuration)
        )
    }
}

fun ParameterHolder.clientConfigOption() = option("--config", help = "Client configuration *.properties")
        .path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)

fun main(args: Array<String>) =
        NoOpCliktCommand(name = "deploy-tool").subcommands(ContainerCommand(), DeployCommand(), UpdateCommand()).main(args)
