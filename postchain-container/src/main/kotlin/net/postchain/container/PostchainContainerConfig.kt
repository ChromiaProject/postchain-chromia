package net.postchain.container

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.server.config.PostchainServerConfig
import java.io.File

data class PostchainContainerConfig(
    val imageName: String,
    val containerName: String?,
    private val configFileName: String,
    val serverConfig: PostchainServerConfig = PostchainServerConfig(),
    val hostName: String = "localhost",
    val volumes: Map<String, String>,
    val resourceLimits: ContainerResourceLimits,
    val activeChainIds: List<Int> = listOf(0),
    val debug: Boolean = true
) {
    val configFile = File(configFileName)
    val appConfig = AppConfig.fromPropertiesFile(configFileName, debug)
    val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
}
