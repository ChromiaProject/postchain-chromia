package net.postchain.deployment

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.d1.common.proposal.proposeBlockchainOperation
import net.postchain.d1.common.proposal.proposeConfigurationOperation
import net.postchain.d1.container.container_proof.createContainerByProofOperation
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellPostAppConfig
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.rell.tools.runcfg.RellRunConfigParams
import net.postchain.rell.utils.DiskGeneralDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class ChromiaDeploymentTool(private val clientProvider: PostchainClientProvider) : DeploymentTool {
    override fun generateConfig(sourceDir: Path, deployXmlFile: Path, outputDir: Path): BlockchainConfigurations {
        val cSourceDir = C_SourceDir.diskDir(sourceDir.toFile())
        val configDir = deployXmlFile.absolute().parent
        val generalConfigDir = DiskGeneralDir(configDir.toFile())
        val params = RellRunConfigParams(cSourceDir, generalConfigDir, RellVersions.VERSION, false)

        val runConfText = DeployXmlParser.parse(deployXmlFile)

        val blockchainConfigurations = extractChainConfig(
                RellRunConfigGenerator.generate(
                        ExceptionCliEnv(),
                        params,
                        deployXmlFile.pathString,
                        runConfText
                )
        )

        Files.createDirectories(outputDir)
        blockchainConfigurations.configurations.forEach { (height, gtvConfig) ->
            // TODO Compression

            val xml = GtvMLEncoder.encodeXMLGtv(gtvConfig)
            outputDir.resolve("$height.xml").writeText(xml)

            val bytes = GtvEncoder.encodeGtv(gtvConfig)
            outputDir.resolve("$height.gtv").writeBytes(bytes)
        }

        return blockchainConfigurations
    }

    private fun extractChainConfig(config: RellPostAppConfig): BlockchainConfigurations {
        val chain = config.chains.first()
        val configs = chain.configs.map { (height, chainConfig) ->
            height to chainConfig.gtvConfig
        }.sortedBy { it.first }
        return BlockchainConfigurations(BlockchainRid(chain.brid.toByteArray()), chain.name, configs)
    }

    override fun createContainer(clientConfig: PostchainClientConfig, containerName: String, clusterName: String, proof: ByteArray) {
        val client = clientProvider.createClient(clientConfig)
        val result = client
                .transactionBuilder()
                .createContainerByProofOperation(
                        clientConfig.signers.first().pubKey.data,
                        name = containerName,
                        clusterName = clusterName,
                        proof,
                        null,
                        null
                )
                .sign()
                .postSyncAwaitConfirmation()
        if (result.status != TransactionStatus.CONFIRMED) {
            throw UserMistake("Deployment failed: ${result.rejectReason ?: "still waiting for confirmation"}")
        }
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
            .postSyncAwaitConfirmation()
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
            .postSyncAwaitConfirmation()
        if (result.status != TransactionStatus.CONFIRMED) {
            throw UserMistake("Update failed: ${result.rejectReason ?: "still waiting for confirmation"}")
        }
    }
}
