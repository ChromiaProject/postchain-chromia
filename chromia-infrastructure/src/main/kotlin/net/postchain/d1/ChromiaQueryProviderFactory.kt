package net.postchain.d1

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.d1.query.LocalQueryProvider
import net.postchain.d1.query.MasterSubQueryProvider
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.subnode.SubConnectionManager

object ChromiaQueryProviderFactory {
    fun create(configuration: BlockchainConfiguration, blockQueriesProvider: BlockQueriesProvider, connectionManager: ConnectionManager, clusterManagement: ClusterManagement): ChromiaQueryProvider {
        // We have the same case here as when we are creating our cluster management
        return if (connectionManager is SubConnectionManager) {
            MasterSubQueryProvider(
                    configuration.blockchainRid,
                    configuration.chainID,
                    connectionManager,
                    clusterManagement,
                    blockQueriesProvider
            )
        } else if (configuration is ManagedDataSourceAware) {
            LocalQueryProvider(
                    configuration.blockchainRid,
                    blockQueriesProvider,
                    clusterManagement,
                    configuration.dataSource
            )
        } else {
            throw ProgrammerMistake("Unable to create query provider for ${configuration.javaClass.name}")
        }
    }
}