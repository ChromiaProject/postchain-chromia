package net.postchain.deployment

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.d1.common.proposal.proposeBlockchainOperation
import net.postchain.d1.common.proposal.proposeConfigurationOperation
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellPostAppConfig
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.rell.tools.runcfg.RellRunConfigParams
import net.postchain.rell.utils.DiskGeneralDir
import net.postchain.rell.utils.RellCliErr
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class ChromiaDeploymentTool(private val clientProvider: PostchainClientProvider) : DeploymentTool {
    override fun generateConfig(sourceDir: Path, deployXmlFile: Path, outputDir: Path): BlockchainConfiguration {
        val cSourceDir = C_SourceDir.diskDir(sourceDir.toFile())
        val configDir = deployXmlFile.absolute().parent
        val generalConfigDir = DiskGeneralDir(configDir.toFile())
        val params = RellRunConfigParams(cSourceDir, generalConfigDir, RellVersions.VERSION, false)

        val parsedConfiguration = DeployXmlParser.parse(deployXmlFile)

        val rellPostAppConfig = try {
            RellRunConfigGenerator.generate(
                    ExceptionCliEnv(),
                    params,
                    deployXmlFile.pathString,
                    parsedConfiguration.runXml
            )
        } catch (e: RellCliErr) {
            val errorMsg = e.message
            if (errorMsg == null) {
                throw UserMistake("Rell parsing failed for unknown reason", e)
            } else if (errorMsg.startsWith(deployXmlFile.pathString) && errorMsg.contains(Regex("\\[path:.*]"))) {
                // Error is related to xml format and contains a run.xml path that needs to be converted
                val (file, element, error, path) = errorMsg.split(":")

                val runXmlRootPath = "run -> chains -> chain"
                val convertedElement = if (path.endsWith("$runXmlRootPath]")) {
                    element.replace("config", "chain")
                } else element

                val convertedPath = path.replace(runXmlRootPath, "deploy")
                        .replace("config", "chain")

                throw UserMistake("$file:$convertedElement:$error:$convertedPath")
            } else {
                throw UserMistake(errorMsg)
            }
        }

        val blockchainConfiguration = extractChainConfig(
                rellPostAppConfig,
                parsedConfiguration
        )

        Files.createDirectories(outputDir)

        // TODO Compression
        val xml = GtvMLEncoder.encodeXMLGtv(blockchainConfiguration.configuration)
        outputDir.resolve("${blockchainConfiguration.blockchainName}.xml").writeText(xml)

        val bytes = GtvEncoder.encodeGtv(blockchainConfiguration.configuration)
        outputDir.resolve("${blockchainConfiguration.blockchainName}.gtv").writeBytes(bytes)

        return blockchainConfiguration
    }

    private fun extractChainConfig(config: RellPostAppConfig, parsedConfiguration: ParsedConfiguration): BlockchainConfiguration {
        val chain = config.chains.first()
        val gtvConfig = chain.configs[0]?.gtvConfig ?: throw ProgrammerMistake("No config found")
        return BlockchainConfiguration(
                parsedConfiguration.blockchainRid,
                BlockchainRid(chain.brid.toByteArray()),
                chain.name,
                parsedConfiguration.containerName,
                gtvConfig
        )
    }

    override fun deployBlockchain(
            clientConfig: PostchainClientConfig,
            blockchainName: String,
            containerName: String,
            configData: ByteArray
    ) {
        val client = clientProvider.createClient(clientConfig)
        val result = client
            .transactionBuilder()
            .proposeBlockchainOperation(
                clientConfig.signers.first().pubKey.data,
                configData,
                blockchainName,
                containerName
            )
            .sign()
            .postAwaitConfirmation()
        if (result.status != TransactionStatus.CONFIRMED) {
            throw UserMistake("Deployment failed: ${result.rejectReason ?: "still waiting for confirmation"}")
        }
    }

    override fun updateBlockchain(
            clientConfig: PostchainClientConfig,
            blockchainRid: BlockchainRid,
            configData: ByteArray
    ) {
        val client = clientProvider.createClient(clientConfig)
        val result = client
            .transactionBuilder()
            .proposeConfigurationOperation(
                clientConfig.signers.first().pubKey.data,
                blockchainRid,
                configData
            )
            .sign()
            .postAwaitConfirmation()
        if (result.status != TransactionStatus.CONFIRMED) {
            throw UserMistake("Update failed: ${result.rejectReason ?: "still waiting for confirmation"}")
        }
    }
}
