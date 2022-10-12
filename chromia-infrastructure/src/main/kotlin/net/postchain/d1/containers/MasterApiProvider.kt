package net.postchain.d1.containers

import net.postchain.config.app.AppConfig
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.ClusterManagementImpl
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource

object MasterApiProvider {

    fun getClusterManagement(appConfig: AppConfig): ClusterManagement {
        return ClusterManagementImpl(
                MasterClientProvider.getChain0Client(appConfig)::querySync
        )
    }

    fun getDirectoryManagement(appConfig: AppConfig): DirectoryDataSource {
        return BaseDirectoryDataSource(
                MasterClientProvider.getChain0Client(appConfig)::querySync,
                appConfig
        )
    }
}