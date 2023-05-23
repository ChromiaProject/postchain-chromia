package net.postchain.d1

import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.Chain0MasterClient
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.subnode.SubConnectionManager

object ClusterManagementFactory {
    /**
     * In the case of master-sub infrastructure, a chain will have a default [GTXBlockchainConfiguration]
     * configuration. [ClusterManagement] uses the master query runner to chain0: [Chain0MasterClient].
     *
     * In case of non-master-sub infrastructure, a chain will be [ManagedDataSourceAware]
     * configuration, therefore [ClusterManagement] uses the local [ManagedNodeDataSource] instance.
     */
    fun create(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): ClusterManagement {
        val query = if (connectionManager is SubConnectionManager) {
            Chain0MasterClient(connectionManager.masterSubQueryManager)::query
        } else if (configuration is ManagedDataSourceAware) {
            { name, gtv -> configuration.dataSource.query(name, gtv) }
        } else {
            throw ProgrammerMistake("Unable to create cluster management for ${configuration.javaClass.name}")
        }
        return ClusterManagementImpl(query)
    }
}