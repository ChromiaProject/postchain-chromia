package net.postchain.client.chromia

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv

class ChromiaClientProvider(val config: PostchainClientConfig) {
    private val chain0Client = ConcretePostchainClient(config)

    fun fromCluster(cluster: String, brid: BlockchainRid): PostchainClient {
        val apiUrls = chain0Client.querySync("get_cluster_api_urls", gtv("name" to gtv(cluster))).asArray().map { it.asString() }
        val chainClientConfig = PostchainClientConfig(
            blockchainRid = brid,
            endpointPool = EndpointPool.default(apiUrls),
            signers = config.signers,
            statusPollCount = config.statusPollCount,
            statusPollInterval = config.statusPollInterval
        )
        return ConcretePostchainClient(chainClientConfig)
    }

    fun fromBlockchainRid(brid: BlockchainRid): PostchainClient {
        val cluster = chain0Client.querySync("get_blockchain_cluster", gtv(brid)).asString()
        return fromCluster(cluster, brid)
    }
}
