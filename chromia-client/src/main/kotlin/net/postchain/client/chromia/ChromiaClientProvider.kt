package net.postchain.client.chromia

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv
import org.http4k.client.ApacheAsyncClient

class ChromiaClientProvider(val config: PostchainClientConfig) {
    private val httpClient = ApacheAsyncClient()
    private val chain0Client = ConcretePostchainClient(config, httpClient)

    fun clusters(): List<String> {
        return chain0Client.querySync("list_clusters").asArray().map { it.asString() }
    }

    fun cluster(cluster: String): ClusterPostchainClient {
        val apiUrls = chain0Client.querySync("get_cluster_api_urls", gtv("name" to gtv(cluster))).asArray().map { it.asString() }
        return ClusterPostchainClient(cluster, EndpointPool.default(apiUrls))
    }

    fun blockchain(brid: BlockchainRid): PostchainClient {
        val apiUrls = chain0Client.querySync("get_blockchain_api_urls", gtv(brid)).asArray().map { it.asString() }
        return ConcretePostchainClient(
            config.copy(
                blockchainRid = brid,
                endpointPool = EndpointPool.default(apiUrls)
            ),
            httpClient
        )
    }

    inner class ClusterPostchainClient(private val name: String, private val endpointPool: EndpointPool) {

        fun blockchains(): List<BlockchainRid> {
            return chain0Client.querySync("get_cluster_blockchains", gtv(name)).asArray().map { BlockchainRid(it.asByteArray()) }
        }

        fun blockchain(brid: BlockchainRid): PostchainClient {
            return ConcretePostchainClient(
                config.copy(
                    blockchainRid = brid,
                    endpointPool = endpointPool
                ),
                httpClient
            )
        }
    }
}
