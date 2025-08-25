package net.postchain.d1.iccf

import net.postchain.PostchainContext
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.PostchainQueryFactory
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.nm_api.NodeManagement
import net.postchain.d1.nm_api.NodeManagementImpl
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.network.common.ConnectionManager

open class IccfGTXModule : SimpleGTXModule<IccfGTXModuleContext>(
        IccfGTXModuleContext(),
        mapOf(IccfProofTxMaterialBuilder.ICCF_OP_NAME to ::IccfGTXOperation),
        mapOf()
), PostchainContextAware, MetadataProvider {

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    IccfProofTxMaterialBuilder.ICCF_OP_NAME to IccfGTXOperation.metadata,
            ),
            queries = mapOf()
    )

    override fun initializeDB(ctx: EContext) {}

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        conf.apply {
            cryptoSystem = postchainContext.cryptoSystem
            clusterManagement = createClusterManagement(configuration, postchainContext.connectionManager)
            nodeManagement = createNodeManagement(configuration, postchainContext.connectionManager)
            queryProvider = ChromiaQueryProviderFactory.create(configuration, postchainContext.blockQueriesProvider, postchainContext.connectionManager, clusterManagement)
            nodeIsReplica = configuration.blockchainContext.nodeID == NODE_ID_READ_ONLY
        }
    }

    open fun createClusterManagement(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): ClusterManagement =
            ClusterManagementImpl(PostchainQueryFactory.create(configuration, connectionManager))

    open fun createNodeManagement(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): NodeManagement =
            NodeManagementImpl(PostchainQueryFactory.create(configuration, connectionManager))
}
