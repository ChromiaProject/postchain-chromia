package net.postchain.deployment.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.deployment.ChromiaDeploymentTool
import net.postchain.deployment.DeploymentTool
import net.postchain.gtv.GtvEncoder
import kotlin.io.path.absolutePathString

abstract class DeployToolCommand(name: String) : CliktCommand(name) {
    val sourceDir by option("-d", "--source-dir").path(mustExist = true, canBeDir = true, canBeFile = false).required()

    val outputDir by option("-o", "--output-dir").path(mustExist = false, canBeDir = true, canBeFile = false).required()

    val clientConfig by option("--config", help = "Client configuration *.properties")
        .path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)

    val deployXmlFile by argument().path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)
}

class DeployCommand : DeployToolCommand(name = "deploy") {
    private val containerName by option("-c", "--container").required()

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

class UpdateCommand : DeployToolCommand(name = "update") {
    private val configVersion by option("-v", "--version").long()

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

fun main(args: Array<String>) =
    NoOpCliktCommand(name = "deploy-tool").subcommands(DeployCommand(), UpdateCommand()).main(args)
