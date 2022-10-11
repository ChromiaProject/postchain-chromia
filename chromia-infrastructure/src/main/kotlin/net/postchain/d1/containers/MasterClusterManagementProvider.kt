package net.postchain.d1.containers

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import java.net.URL
import java.time.Duration

class MasterClusterManagementProvider {

    fun getClusterManagement(appConfig: AppConfig): MasterClusterManagement {
        val restApiUrl = getMasterRestApiUrl(appConfig)
        val clientConfig = PostchainClientConfig(
                blockchainRid = BlockchainRid(byteArrayOf()), // Empty, chainId = 0 will be used
                endpointPool = EndpointPool.singleUrl(restApiUrl),
                failOverConfig = FailOverConfig(attemptsPerEndpoint = 1, attemptInterval = Duration.ZERO),
                queryByChainId = 0
        )

        return MasterClusterManagement(
                ConcretePostchainClientProvider().createClient(clientConfig)
        )
    }

    private fun getMasterRestApiUrl(appConfig: AppConfig): String {
        val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
        val restApiConfig = RestApiConfig.fromAppConfig(appConfig)
        return URL("http",
                containerNodeConfig.masterHost,
                restApiConfig.port,
                restApiConfig.basePath
        ).toString()
    }
}