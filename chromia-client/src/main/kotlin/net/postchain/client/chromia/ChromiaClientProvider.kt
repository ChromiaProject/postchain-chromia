package net.postchain.client.chromia

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv
import org.http4k.client.ApacheAsyncClient

/**
 * Provides postchain clients that can be used to communicate with dapps within the chromia network
 *
 * @param config configuration that points to any set of nodes in the chromia network
 */
class ChromiaClientProvider(val config: PostchainClientConfig) {
    private val httpClient = ApacheAsyncClient()
    private val chain0Client: PostchainClient = ConcretePostchainClient(config, httpClient)

    /**
     * Gets the names of all clusters in the network
     */
    fun clusters(): List<String> {
        return chain0Client.querySync("list_clusters").asArray().map { it.asString() }
    }

    /**
     * Creates a [ClusterPostchainClient] that points to a cluster
     *
     * @param cluster name of the cluster
     */
    fun cluster(cluster: String): ClusterPostchainClient {
        val apiUrls = chain0Client.querySync("get_cluster_api_urls", gtv("name" to gtv(cluster))).asArray().map { it.asString() }
        return ClusterPostchainClient(cluster, EndpointPool.default(apiUrls))
    }

    /**
     * Creates a [PostchainClient] that communicates with a certain blockchain
     *
     * @param brid blockchain rid for the blockchain
     */
    fun blockchain(brid: BlockchainRid): PostchainClient {
        val apiUrls = chain0Client.querySync("get_blockchain_api_urls", gtv(brid)).asArray().map { it.asString() }
        return client(brid, EndpointPool.default(apiUrls))
    }

    /**
     * A client that can spawn [PostchainClient] that are on the same cluster
     */
    inner class ClusterPostchainClient(private val name: String, private val endpointPool: EndpointPool) {

        val blockchains = chain0Client.querySync("get_cluster_blockchains", gtv(name)).asArray().map { BlockchainRid(it.asByteArray()) }

        /**
         * Creates a [PostchainClient] for communicating with a blockchain on cluster [name]
         */
        fun blockchain(brid: BlockchainRid): PostchainClient {
            if (!blockchains.contains(brid)) throw IllegalArgumentException("Blockchain $brid not found on cluster $name")
            return client(brid, endpointPool)
        }
    }

    private fun client(brid: BlockchainRid, endpointPool: EndpointPool) = ConcretePostchainClient(
        config.copy(
            blockchainRid = brid,
            endpointPool = endpointPool
        ),
        httpClient
    )
}
