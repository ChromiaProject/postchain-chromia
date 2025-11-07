package net.postchain.d1.iccf

import net.postchain.PostchainContext
import net.postchain.common.reflection.constructorOf
import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.network.common.ConnectionManager

class IccfTestGTXModule : IccfGTXModule() {
    lateinit var appConfig: AppConfig

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        appConfig = postchainContext.appConfig
        super.initializeContext(configuration, postchainContext, ctx)
    }

    override fun createClusterManagement(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): ClusterManagement =
            constructorOf<ClusterManagement>(appConfig.getString("clusterManagementMock")).newInstance()
}
