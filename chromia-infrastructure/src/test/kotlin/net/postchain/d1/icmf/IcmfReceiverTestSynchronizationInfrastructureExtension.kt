package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.config.DappBlockchainConfiguration

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
    IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createClusterManagement(configuration: DappBlockchainConfiguration) = IcmfTestClusterManagement()

    override fun createClientProvider(clusterManagement: ClusterManagement) =
        PostchainClientMocks.createProvider(clusterManagement)
}
