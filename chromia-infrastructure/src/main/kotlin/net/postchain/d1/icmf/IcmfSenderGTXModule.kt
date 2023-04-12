package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.chromia.cm_api.cmGetSystemChains
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.ClusterManagementFactory
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.network.common.ConnectionManager

open class IcmfSenderGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
), PostchainContextAware {

    private var isSystemChain = false

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        val clusterManagement = createClusterManagement(configuration, postchainContext.connectionManager)
        val queryProvider = createQueryProvider(configuration, clusterManagement, postchainContext)
        isSystemChain = isSystemChain(configuration, queryProvider.getChain0Query())
    }

    override fun initializeDB(ctx: EContext) {}

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
            listOf(IcmfBlockBuilderExtension(isSystemChain))

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf()

    open fun isSystemChain(configuration: BlockchainConfiguration, query: PostchainQuery): Boolean {
        return query.cmGetSystemChains().map { BlockchainRid(it) }.contains(configuration.blockchainRid)
    }

    private fun createClusterManagement(configuration: BlockchainConfiguration, connectionManager: ConnectionManager): ClusterManagement =
            ClusterManagementFactory.create(configuration, connectionManager)

    private fun createQueryProvider(
            configuration: BlockchainConfiguration,
            clusterManagement: ClusterManagement,
            postchainContext: PostchainContext
    ): ChromiaQueryProvider = ChromiaQueryProviderFactory.create(
            configuration, postchainContext.blockQueriesProvider, postchainContext.connectionManager, clusterManagement
    )
}
