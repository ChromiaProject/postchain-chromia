package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.d1.config.MockManagedBlockchainConfigurationProvider
import net.postchain.d1.QueryProviderMocks
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.managed.config.ManagedDataSourceAware

class IcmfReceiverTestSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
    IcmfReceiverSynchronizationInfrastructureExtension(postchainContext) {
    override fun createQueryProvider(
        configuration: BlockchainConfiguration,
        clusterManagement: ClusterManagement
    ): ChromiaQueryProvider = QueryProviderMocks

    override fun createClusterManagement(configuration: BlockchainConfiguration) = IcmfTestClusterManagement()

    override fun createClientProvider(clusterManagement: ClusterManagement) =
        MockPostchainRestApi.createProvider(clusterManagement)

    override fun createBlockchainConfigProvider(configuration: ManagedDataSourceAware, clusterManagement: ClusterManagement) =
            MockManagedBlockchainConfigurationProvider(clusterManagement)
}
