package net.postchain.container

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.server.config.PostchainServerConfig
import java.io.File

data class PostchainContainerConfig(
    val imageName: String,
    val containerName: String?,
    val configFile: File,
    val command: List<String>,
    val serverConfig: PostchainServerConfig = PostchainServerConfig(),
    val volumes: Map<String, String> = mapOf(),
    val env: Map<String, Any> = mapOf(),
    val resourceLimits: ContainerResourceLimits = ContainerResourceLimits.default(),
    val debug: Boolean = true
) {
    val appConfig = AppConfig.fromPropertiesFile(configFile, debug)
    val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
}
