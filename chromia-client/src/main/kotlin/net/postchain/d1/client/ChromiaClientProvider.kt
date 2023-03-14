package net.postchain.d1.client

import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.EndpointPool
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import org.http4k.core.HttpHandler

/**
 * Provides postchain clients that can be used to communicate with dapps within the chromia network
 *
 * @param failOverConfig fail-over configuration
 */
open class ChromiaClientProvider(
        val failOverConfig: FailOverConfig,
        val clusterManagement: ClusterManagement,
        val cryptoSystem: Secp256K1CryptoSystem = Secp256K1CryptoSystem()
) {

    /**
     * Gets the names of all clusters in the network
     */
    open fun clusters(): Collection<String> = clusterManagement.getClusterNames()

    /**
     * Creates a [ClusterPostchainClient] that points to a cluster
     *
     * @param clusterName name of the cluster
     */
    open fun cluster(clusterName: String): ClusterPostchainClient {
        val apiUrls = clusterManagement.getClusterInfo(clusterName).peers.map { it.restApiUrl }
        return ClusterPostchainClient(EndpointPool.default(apiUrls))
    }

    /**
     * Creates a [PostchainClient] that communicates with a certain blockchain
     *
     * @param blockchainRid blockchain rid for the blockchain
     */
    open fun blockchain(blockchainRid: BlockchainRid): PostchainClient {
        val apiUrls = clusterManagement.getBlockchainApiUrls(blockchainRid)
        return client(blockchainRid, EndpointPool.default(apiUrls.toList()))
    }

    /**
     * A client that can spawn [PostchainClient] that are on the same cluster
     */
    open inner class ClusterPostchainClient(private val endpointPool: EndpointPool) {
        /**
         * Creates a [PostchainClient] for communicating with blockchain.
         */
        open fun blockchain(blockchainRid: BlockchainRid): PostchainClient = client(blockchainRid, endpointPool)
    }

    private fun client(blockchainRid: BlockchainRid, endpointPool: EndpointPool) = PostchainClientImpl(
            PostchainClientConfig(
                    failOverConfig = failOverConfig,
                    blockchainRid = blockchainRid,
                    endpointPool = endpointPool,
                    cryptoSystem = cryptoSystem
            )
    )

    companion object {
        /**
         * Builds an instance of [ChromiaClientProvider] that uses a http client to query chain0
         */
        fun fromClientConfig(config: PostchainClientConfig, httpHandler: HttpHandler? = null): ChromiaClientProvider {
            val chain0Client: PostchainClient = httpHandler?.let { PostchainClientImpl(config, it) }
                    ?: PostchainClientImpl(config)
            val clusterManagement = ClusterManagementImpl(chain0Client)
            return ChromiaClientProvider(config.failOverConfig, clusterManagement)
        }
    }
}
