package net.postchain.deployment.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.deployment.ChromiaDeploymentTool
import net.postchain.deployment.DeploymentTool
import net.postchain.gtv.GtvEncoder
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private fun CliktCommand.sourceDirOption() =
        option("-d", "--source-dir", help = "Rell source dir (defaults to 'rell/src')").path(mustExist = true, canBeDir = true, canBeFile = false).default(Path.of("rell/src"))

private fun CliktCommand.outputDirOption() =
        option("-o", "--output-dir", help = "Generated configuration output dir (defaults to 'rell/build')").path(mustExist = false, canBeDir = true, canBeFile = false).default(Path.of("rell/build"))

private fun CliktCommand.deployXmlOption() =
        argument(name = "deploy.xml", help = "(defaults to 'rell/config/deploy.xml')").path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true).default(Path.of("rell/config/deploy.xml"))

private fun ParameterHolder.clientConfigOption() = option("--config", help = "Client configuration *.properties")
        .path(mustExist = true, canBeDir = false, canBeFile = true, mustBeReadable = true)

private fun CliktCommand.showBridOption() = option(help = "Show blockchain rid from this configuration").flag()

class DeployCommand : CliktCommand(name = "deploy", help = "Deploy blockchain into container") {
    private val clientConfig by clientConfigOption()

    private val sourceDir by sourceDirOption()
    private val outputDir by outputDirOption()
    private val deployXmlFile by deployXmlOption()

    private val containerName by option("-c", "--container", help = "Container name")
    private val showBrid by showBridOption()

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(clientConfig?.absolutePathString())

        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        val blockchainConfiguration = deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
        deploymentTool.deployBlockchain(
                clientConfig,
                blockchainConfiguration.blockchainName,
                containerName ?: blockchainConfiguration.containerName ?: throw CliktError("No container specified"),
                GtvEncoder.encodeGtv(blockchainConfiguration.configuration)
        )
        if (showBrid) echo(blockchainConfiguration.generatedBlockchainRid)
    }
}

class UpdateCommand : CliktCommand(name = "update", help = "Update configuration of running blockchain") {
    private val clientConfig by clientConfigOption()

    private val sourceDir by sourceDirOption()
    private val outputDir by outputDirOption()
    private val deployXmlFile by deployXmlOption()

    private val blockchainRid by option("-brid", "--blockchain-rid", help = "Blockchain RID").convert { BlockchainRid.buildFromHex(it) }

    override fun run() {
        val clientConfig = PostchainClientConfig.fromProperties(clientConfig?.absolutePathString())

        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        val blockchainConfiguration = deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
        deploymentTool.updateBlockchain(
                clientConfig,
                blockchainRid ?: blockchainConfiguration.specifiedBlockchainRid
                ?: throw CliktError("No blockchain-rid specified"),
                GtvEncoder.encodeGtv(blockchainConfiguration.configuration)
        )
    }
}

class CompileCommand : CliktCommand(name = "compile", help = "Compile an application and create a blockchain configuration") {
    private val sourceDir by sourceDirOption()
    private val outputDir by outputDirOption()
    private val deployXmlFile by deployXmlOption()
    private val showBrid by showBridOption()

    override fun run() {
        val deploymentTool: DeploymentTool = ChromiaDeploymentTool(ConcretePostchainClientProvider())
        deploymentTool.generateConfig(sourceDir, deployXmlFile, outputDir)
                .apply {
                    if (showBrid) echo(specifiedBlockchainRid ?: generatedBlockchainRid)
                }
    }
}

fun main(args: Array<String>) =
        NoOpCliktCommand(name = "deploy-tool")
                .subcommands(DeployCommand(), UpdateCommand(), CompileCommand())
                .main(args)
