package net.postchain.deployment.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.ArgumentDelegate
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.deployment.ChromiaDeploymentTool
import net.postchain.deployment.DeploymentTool
import net.postchain.devtools.cli.Cli
import net.postchain.gtv.GtvEncoder
import java.nio.file.Path
import kotlin.io.path.absolutePathString


private fun  CliktCommand.sourceDirOption() =
    option("-d", "--source-dir", help = "Rell source dir (defaults to 'rell/src')").path(mustExist = true, canBeDir = true, canBeFile = false).default(Path.of("rell/src"))

private fun CliktCommand.outputDirOption() =
        option("-o", "--output-dir", help = "Generated configuration output dir (defaults to 'rell/build')").path(mustExist = false, canBeDir = true, canBeFile = false).default(Path.of("rell/build"))

private fun CliktCommand.deployXmlOption() =
        argument(name = "deploy.xml", help = "(defaults to 'rell/config/deploy.xml')").path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true).default(Path.of("rell/config/deploy.xml"))

fun ParameterHolder.clientConfigOption() = option("--config", help = "Client configuration *.properties")
        .path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)

abstract class DeployXmlCommand(name: String, help: String) : CliktCommand(name = name, help = help) {
    val clientConfig by clientConfigOption()

    val sourceDir by sourceDirOption()
    val outputDir by outputDirOption()
    val deployXmlFile by deployXmlOption()
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

class CompileCommand : CliktCommand(help = "Compile an application and create a blockchain configuration") {
    private val sourceDir by sourceDirOption()
    private val outputDir by outputDirOption()
    private val deployXmlFile by deployXmlOption()
    private val showBrid by option(help = "Show blockchain rid from this configuration").flag()

    override fun run() {
        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
                .apply {
                    if (showBrid) println(blockchainRid)
                }
    }
}

fun main(args: Array<String>) =
        NoOpCliktCommand(name = "deploy-tool")
                .subcommands(DeployCommand(), UpdateCommand(), CompileCommand())
                .main(args)
