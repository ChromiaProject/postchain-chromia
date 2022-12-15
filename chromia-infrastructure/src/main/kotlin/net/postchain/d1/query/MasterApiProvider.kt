package net.postchain.d1.query

import net.postchain.config.app.AppConfig
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.managed.BaseDirectoryDataSource
import net.postchain.managed.DirectoryDataSource

object MasterApiProvider {

    fun getClusterManagement(appConfig: AppConfig): ClusterManagement {
        return ClusterManagementImpl(
                MasterQueryProvider.getChain0Client(appConfig)
        )
    }

    fun getDirectoryManagement(appConfig: AppConfig): DirectoryDataSource {
        return BaseDirectoryDataSource(
            MasterQueryProvider.getChain0Client(appConfig)::query,
            appConfig
        )
    }
}