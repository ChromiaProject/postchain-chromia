package net.postchain.d1.containers

import net.postchain.config.app.AppConfig
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.ClusterManagementImpl

object MasterClusterManagementProvider {

    fun getClusterManagement(appConfig: AppConfig): ClusterManagement {
        return ClusterManagementImpl(
                MasterClientProvider.getChain0Client(appConfig)::querySync
        )
    }
}