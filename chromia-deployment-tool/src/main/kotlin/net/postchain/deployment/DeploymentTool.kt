package net.postchain.deployment

import net.postchain.client.config.PostchainClientConfig
import net.postchain.common.BlockchainRid
import java.nio.file.Path

interface DeploymentTool {
    fun generateConfig(sourceDir: Path, deployXmlFile: Path, outputDir: Path): BlockchainConfigurations

    fun deployBlockchain(
        clientConfig: PostchainClientConfig,
        blockchainName: String,
        containerName: String,
        configData: ByteArray
    )

    fun updateBlockchain(
        clientConfig: PostchainClientConfig,
        blockchainRid: BlockchainRid,
        configData: ByteArray
    )
}
