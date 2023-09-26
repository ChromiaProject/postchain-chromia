package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.chromia.cm_api.cmGetSystemChains
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.ClusterManagementFactory
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.network.common.ConnectionManager

const val QUERY_ICMF_GET_MESSAGES_AT_HEIGHT = "icmf_get_messages_at_height"
const val QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT = "icmf_get_messages_after_height"

open class IcmfSenderGTXModule : SimpleGTXModule<IcmfSenderGTXModuleContext>(
        IcmfSenderGTXModuleContext(),
        mapOf(),
        mapOf(
                QUERY_ICMF_GET_MESSAGES_AT_HEIGHT to { conf, ctxt, args ->
                    val dict = args as GtvDictionary
                    val topic = dict["topic"]?.asString() ?: throw UserMistake("No topic property supplied")
                    val height = dict["height"]?.asInteger() ?: throw UserMistake("No height property supplied")
                    val messages = conf.dbOperations.getSentMessagesAtHeight(ctxt, topic, height)
                    gtv(messages)
                },
                QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT to { conf, ctxt, args ->
                    val dict = args as GtvDictionary
                    val topic = dict["topic"]?.asString() ?: throw UserMistake("No topic property supplied")
                    val height = dict["height"]?.asInteger() ?: throw UserMistake("No height property supplied")
                    val messages = conf.dbOperations.getSentMessagesAfterHeight(ctxt, topic, height)
                    gtv(messages.map { gtv(mapOf("body" to it.body, "height" to gtv(it.height))) })
                }
        )
), PostchainContextAware {

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        val clusterManagement = createClusterManagement(configuration, postchainContext.connectionManager)
        val queryProvider = createQueryProvider(configuration, clusterManagement, postchainContext)
        conf.isSystemChain = configuration.chainID == 0L || isSystemChain(configuration, queryProvider.getChain0Query())
    }

    override fun initializeDB(ctx: EContext) {
        conf.dbOperations.initialize(ctx)
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
            listOf(IcmfBlockBuilderExtension(conf.isSystemChain, conf.dbOperations))

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
