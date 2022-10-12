package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.d1.cluster.ClusterManagement

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
        IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClusterManagement(configuration: BlockchainConfiguration) = IcmfTestClusterManagement()

    override fun createClientProvider(clusterManagement: ClusterManagement) =
            PostchainClientMocks.createProvider(clusterManagement)
}
