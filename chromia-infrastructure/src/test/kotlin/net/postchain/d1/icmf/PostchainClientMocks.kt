package net.postchain.d1.icmf

import net.postchain.client.config.FailOverConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement

object PostchainClientMocks {
    private val mockClients = mutableMapOf<BlockchainRid, PostchainClient>()

    fun createProvider(clusterManagement: ClusterManagement): MockPostchainClientProvider {
        return MockPostchainClientProvider(clusterManagement)
    }

    fun addMockClient(blockchainRid: BlockchainRid, client: PostchainClient) {
        mockClients[blockchainRid] = client
    }

    fun clearMocks() {
        mockClients.clear()
    }

    class MockPostchainClientProvider(clusterManagement: ClusterManagement) :
        ChromiaClientProvider(FailOverConfig(), clusterManagement) {
        override fun cluster(clusterName: String): ClusterPostchainClient {
            return object : ClusterPostchainClient(EndpointPool.default(listOf("notused"))) {
                override fun blockchain(blockchainRid: BlockchainRid): PostchainClient = mockClients[blockchainRid]
                    ?: throw ProgrammerMistake("No client defined for bc-rid: $blockchainRid")
            }
        }
    }
}
