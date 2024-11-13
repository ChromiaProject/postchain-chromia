package net.postchain.d1

import net.postchain.client.core.PostchainQuery
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.d1.query.Chain0MasterClient
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.subnode.SubConnectionManager

object PostchainQueryFactory {

    /**
     * In the case of master-sub infrastructure, a chain will have a default [GTXBlockchainConfiguration]
     * configuration. [PostchainQuery] uses the master query runner to chain0: [Chain0MasterClient].
     *
     * In case of non-master-sub infrastructure, a chain will be [ManagedDataSourceAware]
     * configuration, therefore [PostchainQuery] uses the local [ManagedNodeDataSource] instance.
     */
    fun create(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): PostchainQuery {
        return if (connectionManager is SubConnectionManager) {
            Chain0MasterClient(connectionManager.masterSubQueryManager)
        } else if (configuration is ManagedDataSourceAware) {
            PostchainQuery { name, gtv -> configuration.dataSource.query(name, gtv) }
        } else {
            throw ProgrammerMistake("Unable to create cluster management for ${configuration.javaClass.name}")
        }
    }
}