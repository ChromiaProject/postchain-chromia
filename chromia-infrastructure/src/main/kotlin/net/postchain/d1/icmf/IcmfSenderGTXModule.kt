package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.chromia.cm_api.cmGetSystemChains
import net.postchain.client.core.PostchainQuery
import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.d1.ChromiaQueryProviderFactory
import net.postchain.d1.PostchainQueryFactory
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.QueryMetadata
import net.postchain.gtx.ReturnMetadata
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.network.common.ConnectionManager

/**
 * query icmf_get_messages_at_height(topic: text, height: integer): list<gtv>
 */
const val QUERY_ICMF_GET_MESSAGES_AT_HEIGHT = "icmf_get_messages_at_height"

/**
 * struct message_at_height {
 *     height: integer;
 *     body: gtv;
 * }
 *
 * query icmf_get_messages_after_height(topic: text, height: integer): list<message_at_height>
 */
const val QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT = "icmf_get_messages_after_height"

/**
 * query icmf_get_all_topics(): list<text>
 */
const val QUERY_ICMF_GET_ALL_TOPICS = "icmf_get_all_topics"

/**
 * struct message_at_height {
 *     height: integer;
 *     body: gtv;
 * }
 *
 * query icmf_get_messages_before_height(topic: text, height: integer): list<message_at_height>
 */
const val QUERY_ICMF_GET_MESSAGES_BEFORE_HEIGHT = "icmf_get_messages_before_height"

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
                    val messages = conf.dbOperations.getSentMessagesAfterHeight(ctxt, topic, height, conf.messageQueryLimit)
                    gtv(messages.map { gtv(mapOf("body" to it.body, "height" to gtv(it.height))) })
                },
                QUERY_ICMF_GET_ALL_TOPICS to { conf, ctxt, args ->
                    val topics = conf.dbOperations.getAllTopics(ctxt)
                    gtv(topics.map { gtv(it) })
                },
                QUERY_ICMF_GET_MESSAGES_BEFORE_HEIGHT to { conf, ctxt, args ->
                    val dict = args as GtvDictionary
                    val topic = dict["topic"]?.asString() ?: throw UserMistake("No topic property supplied")
                    val height = dict["height"]?.asInteger() ?: throw UserMistake("No height property supplied")
                    val messages = conf.dbOperations.getSentMessagesBeforeHeight(ctxt, topic, height, conf.messageQueryLimit)
                    gtv(messages.map { gtv(mapOf("body" to it.body, "height" to gtv(it.height))) })
                },
        )
), PostchainContextAware, MetadataProvider {

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(),
            queries = mapOf(
                    QUERY_ICMF_GET_MESSAGES_AT_HEIGHT to QueryMetadata(args = listOf(
                            ArgumentMetadata(name = "topic", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(name = "height", gtvTypes = setOf(GtvType.INTEGER)),
                    ), returnType = ReturnMetadata(gtvTypes = setOf(GtvType.ARRAY))),
                    QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT to QueryMetadata(args = listOf(
                            ArgumentMetadata(name = "topic", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(name = "height", gtvTypes = setOf(GtvType.INTEGER)),
                    ), returnType = ReturnMetadata(gtvTypes = setOf(GtvType.ARRAY))),
                    QUERY_ICMF_GET_ALL_TOPICS to QueryMetadata(args = listOf(),
                            returnType = ReturnMetadata(gtvTypes = setOf(GtvType.ARRAY))),
                    QUERY_ICMF_GET_MESSAGES_BEFORE_HEIGHT to QueryMetadata(args = listOf(
                            ArgumentMetadata(name = "topic", gtvTypes = setOf(GtvType.STRING)),
                            ArgumentMetadata(name = "height", gtvTypes = setOf(GtvType.INTEGER)),
                    ), returnType = ReturnMetadata(gtvTypes = setOf(GtvType.ARRAY))),
            )
    )

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        val clusterManagement = createClusterManagement(configuration, postchainContext.connectionManager)
        val queryProvider = createQueryProvider(configuration, clusterManagement, postchainContext)
        conf.isSystemChain = configuration.chainID == 0L || isSystemChain(configuration, queryProvider.getChain0Query())
        conf.messageQueryLimit = configuration.rawConfig["icmf"]?.get("sender")?.let {
            val queryLimit = IcmfSenderBlockchainConfigData.fromGtv(it).messageQueryLimit.toInt()
            if (queryLimit > 0) queryLimit else DEFAULT_MESSAGE_QUERY_LIMIT
        } ?: DEFAULT_MESSAGE_QUERY_LIMIT
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
            ClusterManagementImpl(PostchainQueryFactory.create(configuration, connectionManager))

    private fun createQueryProvider(
            configuration: BlockchainConfiguration,
            clusterManagement: ClusterManagement,
            postchainContext: PostchainContext
    ): ChromiaQueryProvider = ChromiaQueryProviderFactory.create(
            configuration, postchainContext.blockQueriesProvider, postchainContext.connectionManager, clusterManagement
    )
}
