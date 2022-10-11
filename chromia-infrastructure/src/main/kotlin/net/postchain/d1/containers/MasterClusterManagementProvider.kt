package net.postchain.d1.containers

import net.postchain.config.app.AppConfig

object MasterClusterManagementProvider {

    fun getClusterManagement(appConfig: AppConfig): MasterClusterManagement {
        return MasterClusterManagement(
                MasterClientProvider.getChain0Client(appConfig)
        )
    }
}