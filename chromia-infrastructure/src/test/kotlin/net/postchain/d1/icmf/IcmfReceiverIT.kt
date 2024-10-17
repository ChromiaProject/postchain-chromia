package net.postchain.d1.icmf

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainBlockClient
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.d1.QueryProviderMocks
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_BODY
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_HEIGHT
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_SENDER
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_TOPIC
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.testMessageTable
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.getModules
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.collections.eachCount
import kotlin.math.ceil

class IcmfReceiverIT : ManagedModeTest() {

    private val systemAnchoringChain = BlockchainRid.buildRepeat(0)
    private val clusterAnchoringChain = BlockchainRid.buildRepeat(1)

    private val senderOneChainRid = BlockchainRid.buildRepeat(2)
    private val senderOneMessageBody = gtv("hej1")
    private val senderOneEncodedMessageBody = GtvEncoder.encodeGtv(senderOneMessageBody)
    private val senderOneQueryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(senderOneMessageBody))
    private val senderOneSecondQueryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(senderOneMessageBody), 1, 0)

    private val senderTwoChainRid = BlockchainRid.buildRepeat(3)
    private val senderTwoMessageBody = gtv("hej2")
    private val senderTwoEncodedMessageBody = GtvEncoder.encodeGtv(senderTwoMessageBody)
    private val senderTwoQueryResponse = createQueryResponseForMessage(senderTwoChainRid, listOf(senderTwoMessageBody))

    companion object {
        @BeforeAll
        @JvmStatic
        fun start() {
            MockPostchainRestApi.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            MockPostchainRestApi.close()
        }
    }

    @BeforeEach
    @AfterEach
    fun shutdown() {
        MockPostchainRestApi.clearMocks()
    }

    /**
     * @param anchorQueryResponse Pass multiple responses to mock sending at multiple heights
     * @param messageQueryResponse Pass the messages to send at each height
     */
    private fun setupClientMocks(anchorQueryResponse: List<Gtv> = listOf(senderOneQueryResponse), messageQueryResponse: List<Gtv> = listOf(senderOneMessageBody)) {
        val clusterAnchoringChainClientMock: PostchainClient = mock {}
        val senderOneChainClientMock: PostchainClient = mock {}

        anchorQueryResponse.forEachIndexed { index, response ->
            val messageHeight = index.toLong()
            doReturn(buildAnchorHeader(listOf(response["block_header"]!!.asByteArray()), messageHeight - 1))
                    .whenever(clusterAnchoringChainClientMock).blockAtHeight(messageHeight)

            doReturn(gtv(listOf(response))).whenever(clusterAnchoringChainClientMock).query(
                    "icmf_get_headers_with_messages_after_height",
                    gtv(mapOf(
                            "topic" to gtv("my-topic"),
                            "from_anchor_height" to gtv(messageHeight - 1)
                    ))
            )

            doReturn(gtv(messageQueryResponse)).whenever(senderOneChainClientMock).query(
                    QUERY_ICMF_GET_MESSAGES_AT_HEIGHT,
                    gtv(mapOf(
                            "topic" to gtv("my-topic"),
                            "height" to gtv(messageHeight)
                    ))
            )
        }

        MockPostchainRestApi.addMockClient(clusterAnchoringChain, clusterAnchoringChainClientMock)
        MockPostchainRestApi.addMockClient(senderOneChainRid, senderOneChainClientMock)
    }

    private fun setupQueriesMocks() {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.clusterAnchoringQueries = object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    buildAnchorHeader(listOf(senderTwoQueryResponse["block_header"]!!.asByteArray()))

            override fun query(name: String, args: Gtv): Gtv =
                    if (name == "icmf_get_headers_with_messages_after_height" && args["topic"] == gtv("my-topic") && args["from_anchor_height"] == gtv(
                                    -1
                            )
                    )
                        gtv(listOf(senderTwoQueryResponse))
                    else if (name == "icmf_get_headers_with_messages_after_height")
                        gtv(listOf())
                    else
                        GtvNull
        }

        QueryProviderMocks.addMockQueries(senderTwoChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = throw NotImplementedError()

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AT_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(0))
                        gtv(listOf(senderTwoMessageBody))
                    else
                        GtvNull
        })
    }

    private fun setupNonAnchoredQueriesMock(senderBrid: BlockchainRid = senderTwoChainRid, messageHeight: Long = 0, prevMessageHeight: Long = -1) {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.addMockQueries(senderBrid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == messageHeight) createBlockDetail(senderBrid, listOf(senderTwoMessageBody), "my-topic", messageHeight, prevMessageHeight) else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"]!!.asInteger() < messageHeight)
                        gtv(listOf(gtv(mapOf("body" to senderTwoMessageBody, "height" to gtv(messageHeight)))))
                    else
                        gtv(listOf())
        })
    }

    private fun setupFailingNonAnchoredQueriesMock() {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.addMockQueries(senderOneChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == 0L) createBlockDetail(senderOneChainRid, listOf(senderOneMessageBody), "failing-topic") else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("failing-topic") && args["height"] == gtv(-1))
                        gtv(listOf(
                                gtv(mapOf("body" to senderOneMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })

        QueryProviderMocks.addMockQueries(senderTwoChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == 0L) createBlockDetail(senderTwoChainRid, listOf(senderTwoMessageBody), "my-topic") else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(
                                gtv(mapOf("body" to senderTwoMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun globalTopicReceiver() {
        setupClientMocks(listOf(senderOneQueryResponse, senderOneSecondQueryResponse))
        setupQueriesMocks()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_global_1.xml")!!.readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assertThat(messages).hasSize(3)
                        assertThat(messages.filter { it.sender == senderOneChainRid && it.topic == "my-topic" && it.body.contentEquals(senderOneEncodedMessageBody) }).hasSize(2)
                        assertThat(messages.any { it.sender == senderTwoChainRid && it.topic == "my-topic" && it.body.contentEquals(senderTwoEncodedMessageBody) }).isTrue()
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun interClusterSpecificChainReceiver() {
        setupClientMocks()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_inter_cluster_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assertThat(messages).hasSize(1)
                        val message = messages[0]
                        assertThat(message.sender).isEqualTo(senderOneChainRid)
                        assertThat(message.topic).isEqualTo("my-topic")
                        assertThat(message.body.contentEquals(senderOneEncodedMessageBody)).isTrue()
                    }
                }
            }
        }

        verifyPipesAreEmpty(dappChain)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun intraClusterSpecificChainReceiverWithoutAnchoring() {
        setupNonAnchoredQueriesMock()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_intra_cluster_without_anchoring_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(senderTwoChainRid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun failingReceiverShouldNotBlockOtherPipes() {
        setupFailingNonAnchoredQueriesMock()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_failing_receiver_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(senderTwoChainRid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                }
            }
        }

        buildBlock(dappChain)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun intraClusterSpecificChainReceiver() {
        setupQueriesMocks()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_intra_cluster_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assertThat(messages).hasSize(1)
                        val message = messages[0]
                        assertThat(message.sender).isEqualTo(senderTwoChainRid)
                        assertThat(message.topic).isEqualTo("my-topic")
                        assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun anchoringReceiver() {
        QueryProviderMocks.clearMocks()
        QueryProviderMocks.addMockQueries(clusterAnchoringChain, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = createBlockDetail(clusterAnchoringChain, listOf(senderOneMessageBody), "my-topic")

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(gtv(mapOf("body" to senderOneMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })

        QueryProviderMocks.addMockQueries(systemAnchoringChain, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = createBlockDetail(systemAnchoringChain, listOf(senderTwoMessageBody), "my-topic")

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(gtv(mapOf("body" to senderTwoMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_anchoring_receiver_1.xml")!!.readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assertThat(messages).hasSize(2)
                        assertThat(messages.any { it.sender == clusterAnchoringChain && it.topic == "my-topic" && it.body.contentEquals(senderOneEncodedMessageBody) }).isTrue()
                        assertThat(messages.any { it.sender == systemAnchoringChain && it.topic == "my-topic" && it.body.contentEquals(senderTwoEncodedMessageBody) }).isTrue()
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun maxMessageSize() {
        val context = LoggerContext.getContext(false)
        val logger = context.getLogger(InterClusterAnchoredTopicPipe::class.java)
        val appender = ListAppender("List").apply {
            start()
        }
        context.configuration.addLoggerAppender(logger as Logger, appender)

        val messageBody = gtv("imtoobig".repeat(2 * 1024 * 1024))
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)
        val queryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(messageBody))

        setupClientMocks(listOf(queryResponse), listOf(messageBody))

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_inter_cluster_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            assertThat(appender.events.map { it.message.toString() })
                    .contains("Message with size ${encodedMessageBody.size} bytes exceeds maximum size: $MAX_MESSAGE_SIZE bytes")
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun spilledMessages() {
        val messageBody = gtv("m".repeat(90 * 1024))
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)
        val secondMessageBody = gtv("n".repeat(90 * 1024))
        val secondEncodedMessageBody = GtvEncoder.encodeGtv(secondMessageBody)
        val queryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(messageBody, secondMessageBody))

        setupClientMocks(listOf(queryResponse), listOf(messageBody, secondMessageBody))

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_spilled_messages_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assertThat(messages).hasSize(2)
                        val firstMessage = messages[0]
                        assertThat(firstMessage.sender).isEqualTo(senderOneChainRid)
                        assertThat(firstMessage.topic).isEqualTo("my-topic")
                        assertThat(firstMessage.body.contentEquals(encodedMessageBody)).isTrue()

                        val secondMessage = messages[1]
                        assertThat(secondMessage.sender).isEqualTo(senderOneChainRid)
                        assertThat(secondMessage.topic).isEqualTo("my-topic")
                        assertThat(secondMessage.body.contentEquals(secondEncodedMessageBody)).isTrue()

                        assertThat(firstMessage.height).isLessThan(secondMessage.height)
                    }
                }
            }
        }

        verifyPipesAreEmpty(dappChain)
    }

    @ParameterizedTest
    @CsvSource(
            "1, 1",
            "10, 1",
            "10, 2",
            "10, 3",
            "10, 9",
            "10, 10",
    )
    fun messageLimit(messageCount: Int, messageLimit: Int) {

        val messages = (1..messageCount).map {
            val messageBody = gtv("msg_$it")
            messageBody
        }
        val expectedNumberOfBlocks = ceil(messageCount / messageLimit.toDouble()).toInt()

        val queryResponse = createQueryResponseForMessage(senderOneChainRid, messages)
        setupClientMocks(listOf(queryResponse), messages)

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_message_limit_1.xml")!!
                        .readText()
                        .replace("__MESSAGE_LIMIT__", "$messageLimit")
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)

            for (node in getChainNodes(dappChain)) {
                withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }
                        val messagesPerHeight = messages.groupingBy { it.height }
                                .eachCount()

                        // Expect all messages to be received
                        assertThat(messages).hasSize(messageCount)

                        // Expect message distribution over blocks
                        assertThat(messagesPerHeight).hasSize(expectedNumberOfBlocks)

                        // Expect no block to contain more than messageLimit messages
                        assertThat(messagesPerHeight.all { (_, messagesInHeight) -> messagesInHeight <= messageLimit }).isTrue()
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun directoryChainReceiver() {
        startManagedSystem(3, 0)

        val directoryChainBrid = withReadConnection(nodes[0].postchainContext.blockBuilderStorage, 0L) { ctx ->
            DatabaseAccess.of(ctx).getBlockchainRid(ctx)!!
        }
        setupNonAnchoredQueriesMock(directoryChainBrid)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_directory_chain_receiver_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(directoryChainBrid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun skipToHeight() {
        setupNonAnchoredQueriesMock()

        startManagedSystem(3, 0)

        // Skips to height 2
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_skip_to_height_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).isEmpty()
                }
            }
        }

        setupNonAnchoredQueriesMock(messageHeight = 2)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(senderTwoChainRid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    assertThat(message.height).isEqualTo(1)
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun skipToHeightAfterReadingMessages() {
        setupNonAnchoredQueriesMock()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_intra_cluster_without_anchoring_1.xml")!!
                        .readText()
        )

        val dappChain = startNewBlockchain(
                setOf(0, 1, 2),
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig)
        )

        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(senderTwoChainRid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    assertThat(message.height).isEqualTo(0)
                }
            }
        }

        val skipToHeightConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_skip_to_height_1.xml")!!
                        .readText()
        )

        // Restart with skip to height set to 2
        addDappBlockchainConfiguration(
                dappChain, GtvEncoder.encodeGtv(skipToHeightConfig), 2
        )

        buildBlockNoWait(nodes, dappChain, 2)
        awaitChainRestarted(dappChain, 1, skipToHeightConfig.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))

        // skip config should be applied, verify that we don't care about messages at height 1
        setupNonAnchoredQueriesMock(messageHeight = 1, prevMessageHeight = 0)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(1)
                    val message = messages[0]
                    assertThat(message.sender).isEqualTo(senderTwoChainRid)
                    assertThat(message.topic).isEqualTo("my-topic")
                    assertThat(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    assertThat(message.height).isEqualTo(0)
                }
            }
        }

        // See that we read message at height 2
        setupNonAnchoredQueriesMock(messageHeight = 2, prevMessageHeight = 1)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                    val messages = jooq.select()
                            .from(tableName(ctx, testMessageTable))
                            .fetch()
                            .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                    assertThat(messages).hasSize(2)
                    messages.forEach {
                        assertThat(it.sender).isEqualTo(senderTwoChainRid)
                        assertThat(it.topic).isEqualTo("my-topic")
                        assertThat(it.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    }
                    assertThat(messages[0].height).isEqualTo(0)
                    assertThat(messages[1].height).isEqualTo(3)
                }
            }
        }
    }

    private fun verifyPipesAreEmpty(dappChain: Long) {
        // Let all nodes be primary once, so they can clean their pipes
        repeat(nodes.size) {
            buildBlock(dappChain)
        }

        nodes.forEach { node ->
            val receiverGTXModule = node.getModules(1L).find { it is IcmfReceiverGTXModule }!!
            val receiverSpecialTxExtension = receiverGTXModule.getSpecialTxExtensions()[0] as IcmfReceiverSpecialTxExtension
            val globalTopicPipe = receiverSpecialTxExtension.globalTopicReceivers[0].getRelevantPipes()[0] as InterClusterAnchoredTopicPipe

            assertThat(globalTopicPipe.queueIsEmpty).isTrue()
        }
    }

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, messageBodies: List<Gtv>, messageHeight: Long = 0, prevMessageHeight: Long = -1): Gtv {
        val blockDetail = createBlockDetail(blockchainRid, messageBodies, "my-topic", messageHeight, prevMessageHeight)
        return gtv(
                mapOf(
                        "block_header" to gtv(blockDetail.header),
                        "witness" to gtv(blockDetail.witness),
                        "anchor_height" to gtv(messageHeight)
                )
        )
    }

    private fun createBlockDetail(blockchainRid: BlockchainRid, messageBodies: List<Gtv>, topic: String, messageHeight: Long = 0, prevMessageHeight: Long = -1): BlockDetail {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val blockHeader = BlockHeaderData(
                gtv(blockchainRid.data),
                gtv(ByteArray(32) { messageHeight.toByte() }),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(messageHeight),
                GtvNull,
                gtv(
                        mapOf(
                                ICMF_BLOCK_HEADER_EXTRA to gtv(
                                        topic to TopicHeaderData(
                                                gtv(listOf(gtv(messageBodies.map { gtv(it.merkleHash(hashCalculator)) }))).merkleHash(hashCalculator),
                                                prevMessageHeight
                                        ).toGtv()
                                )
                        )
                )
        )
        val gtvBlockHeader = blockHeader.toGtv()
        val blockRid = gtvBlockHeader.merkleHash(hashCalculator)
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(
                        cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(blockRid)
                )
        ).getRawData()

        return BlockDetail(
                blockRid.wrap(),
                blockHeader.getPreviousBlockRid().wrap(),
                GtvEncoder.encodeGtv(gtvBlockHeader).wrap(),
                messageHeight,
                listOf(),
                rawWitness.wrap(),
                blockHeader.getTimestamp()
        )
    }

    private fun buildAnchorHeader(icmfHeaders: List<ByteArray>, prevHeight: Long = -1): BlockDetail {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val icmfBlockRids = icmfHeaders.map {
            val decodedHeader = BlockHeaderData.fromBinary(it)
            val blockRid = decodedHeader.toGtv().merkleHash(hashCalculator)
            gtv(blockRid)
        }

        val blockHeader = BlockHeaderData(
                gtv(clusterAnchoringChain.data),
                gtv(clusterAnchoringChain.data),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(prevHeight + 1),
                GtvNull,
                gtv(
                        mapOf(
                                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                                        "my-topic" to TopicHeaderData(gtv(icmfBlockRids).merkleHash(hashCalculator), prevHeight).toGtv()
                                )),
                        )
                )
        ).toGtv()
        val blockRid = blockHeader.merkleHash(hashCalculator)
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(
                        cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(blockRid)
                )
        ).getRawData()
        return BlockDetail(
                blockRid.wrap(),
                clusterAnchoringChain.data.wrap(),
                GtvEncoder.encodeGtv(blockHeader).wrap(),
                0L,
                listOf(),
                rawWitness.wrap(),
                0L
        )
    }

    class TestMessage(
            val sender: BlockchainRid,
            val topic: String,
            val body: ByteArray,
            val height: Long
    )
}
