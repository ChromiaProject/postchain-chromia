package net.postchain.d1.containers

import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.ContainerNodeConfig
import java.net.URL
import java.time.Duration

object MasterClientProvider {

    fun getClient(appConfig: AppConfig, blockchainRid: BlockchainRid): PostchainClient {
        return ConcretePostchainClientProvider().createClient(
                buildConfig(appConfig, blockchainRid = blockchainRid)
        )
    }

    fun getChain0Client(appConfig: AppConfig): PostchainClient {
        return ConcretePostchainClientProvider().createClient(
                buildConfig(appConfig, queryByChainId = 0L)
        )
    }

    private fun buildConfig(
            appConfig: AppConfig,
            blockchainRid: BlockchainRid = BlockchainRid(byteArrayOf()),
            queryByChainId: Long? = null
    ): PostchainClientConfig {
        val restApiUrl = getMasterRestApiUrl(appConfig)
        return PostchainClientConfig(
                blockchainRid = blockchainRid,
                endpointPool = EndpointPool.singleUrl(restApiUrl),
                failOverConfig = FailOverConfig(attemptsPerEndpoint = 1, attemptInterval = Duration.ZERO),
                queryByChainId = queryByChainId
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