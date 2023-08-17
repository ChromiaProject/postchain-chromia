package net.postchain.d1.anchoring

import net.postchain.PostchainContext
import net.postchain.common.reflection.constructorOf
import net.postchain.core.BlockchainInfrastructure
import net.postchain.d1.MockManagedBlockchainConfigurationProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.managed.config.ManagedDataSourceAware

class AnchoringTestProcessManagerExtension(private val postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure)
    : AnchoringProcessManagerExtension(postchainContext, blockchainInfrastructure) {
    override fun createClusterManagement(configuration: ManagedDataSourceAware) =
            constructorOf<ClusterManagement>(postchainContext.appConfig.getString("clusterManagementMock")).newInstance()

    override fun createBlockchainConfigProvider(configuration: ManagedDataSourceAware, clusterManagement: ClusterManagement) =
            MockManagedBlockchainConfigurationProvider(clusterManagement)
}
