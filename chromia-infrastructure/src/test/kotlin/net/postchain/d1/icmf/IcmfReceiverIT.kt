package net.postchain.d1.icmf

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import assertk.assertions.isZero
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
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.clusterAnchoringChainRid
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.localSenderChainRid
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.localSenderChainRid3
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.localSenderChainRid2
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.remoteSenderChainRid
import net.postchain.d1.icmf.IcmfTestClusterManagement.Companion.systemAnchoringChainRid
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GtxOp
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class IcmfReceiverIT : IcmfBaseIT() {

    private val remoteSenderMessageBody = gtv("remote")
    private val remoteSenderEncodedMessageBody = GtvEncoder.encodeGtv(remoteSenderMessageBody)
    private val remoteSenderQueryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(remoteSenderMessageBody))
    private val remoteSenderSecondQueryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(remoteSenderMessageBody), 1, 0)

    private val localSenderMessageBody = gtv("local")
    private val localSenderEncodedMessageBody = GtvEncoder.encodeGtv(localSenderMessageBody)
    private val localSenderQueryResponse = createQueryResponseForMessage(localSenderChainRid, listOf(localSenderMessageBody))

    private val otherLocalSenderMessageBody = gtv("other")
    private val dbOperations = IcmfDatabaseOperationsImpl()

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
    private fun setupClientMocks(anchorQueryResponse: List<Gtv> = listOf(remoteSenderQueryResponse), messageQueryResponse: List<Gtv> = listOf(remoteSenderMessageBody), topic: String = "my-topic") {
        val clusterAnchoringChainClientMock: PostchainClient = mock {}
        val senderOneChainClientMock: PostchainClient = mock {}

        anchorQueryResponse.forEachIndexed { index, response ->
            val messageHeight = index.toLong()
            doReturn(buildAnchorHeader(listOf(response["block_header"]!!.asByteArray()), messageHeight - 1, topic))
                    .whenever(clusterAnchoringChainClientMock).blockAtHeight(messageHeight)

            doReturn(gtv(listOf(response))).whenever(clusterAnchoringChainClientMock).query(
                    "icmf_get_headers_with_messages_after_height",
                    gtv(mapOf(
                            "topic" to gtv(topic),
                            "from_anchor_height" to gtv(messageHeight - 1)
                    ))
            )

            doReturn(gtv(messageQueryResponse)).whenever(senderOneChainClientMock).query(
                    QUERY_ICMF_GET_MESSAGES_AT_HEIGHT,
                    gtv(mapOf(
                            "topic" to gtv(topic),
                            "height" to gtv(messageHeight)
                    ))
            )
        }

        MockPostchainRestApi.addMockClient(clusterAnchoringChainRid, clusterAnchoringChainClientMock)
        MockPostchainRestApi.addMockClient(remoteSenderChainRid, senderOneChainClientMock)
    }

    private fun setupNonAnchoredClientMocks(senderChainRid: BlockchainRid = remoteSenderChainRid, messageHeight: Long = 0, prevMessageHeight: Long = -1, topic: String = "my-topic") {
        val senderChainClientMock: PostchainClient = mock {}

        doReturn(createBlockDetail(senderChainRid, listOf(remoteSenderMessageBody), topic, messageHeight, prevMessageHeight))
                .whenever(senderChainClientMock).blockAtHeight(messageHeight)

        doReturn(gtv(listOf())).whenever(senderChainClientMock).query(
                eq(QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT),
                any()
        )

        doReturn(gtv(listOf(gtv(mapOf("body" to remoteSenderMessageBody, "height" to gtv(messageHeight)))))).whenever(senderChainClientMock).query(
                QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT,
                gtv(mapOf(
                        "topic" to gtv(topic),
                        "height" to gtv(messageHeight - 1)
                ))
        )

        MockPostchainRestApi.addMockClient(senderChainRid, senderChainClientMock)
    }

    private fun setupQueriesMocks(localSenderQueryResponse: Gtv = this.localSenderQueryResponse, topic: String = "my-topic") {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.clusterAnchoringQueries = object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    buildAnchorHeader(listOf(localSenderQueryResponse["block_header"]!!.asByteArray()), topic = topic)

            override fun query(name: String, args: Gtv): Gtv =
                    if (name == "icmf_get_headers_with_messages_after_height" && args["topic"] == gtv(topic) && args["from_anchor_height"] == gtv(
                                    -1
                            )
                    )
                        gtv(listOf(localSenderQueryResponse))
                    else if (name == "icmf_get_headers_with_messages_after_height")
                        gtv(listOf())
                    else
                        GtvNull
        }

        QueryProviderMocks.addMockQueries(localSenderChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = throw NotImplementedError()

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AT_HEIGHT && args["topic"] == gtv(topic) && args["height"] == gtv(0))
                        gtv(listOf(localSenderMessageBody))
                    else
                        GtvNull
        })
    }

    private fun setupNonAnchoredQueriesMock(senderBrid: BlockchainRid = localSenderChainRid, messageHeight: Long = 0, prevMessageHeight: Long = -1, topic: String = "my-topic") {
        QueryProviderMocks.clearMocks()

        // Set no messages as default for all local senders
        listOf(localSenderChainRid, localSenderChainRid2, localSenderChainRid3).forEach {
            QueryProviderMocks.addMockQueries(it, object : PostchainBlockClient {
                override fun blockAtHeight(height: Long) = null
                override fun query(name: String, args: Gtv) = gtv(listOf())
            })
        }

        addNonAnchoredQueriesMock(senderBrid, messageHeight, prevMessageHeight, topic)
    }

    private fun addNonAnchoredQueriesMock(senderBrid: BlockchainRid = localSenderChainRid, messageHeight: Long = 0, prevMessageHeight: Long = -1, topic: String = "my-topic", messageBody: Gtv = localSenderMessageBody) {

        QueryProviderMocks.addMockQueries(senderBrid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == messageHeight) createBlockDetail(senderBrid, listOf(messageBody), topic, messageHeight, prevMessageHeight) else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv(topic) && args["height"]!!.asInteger() < messageHeight)
                        gtv(listOf(gtv(mapOf("body" to messageBody, "height" to gtv(messageHeight)))))
                    else
                        gtv(listOf())
        })
    }

    private fun setupFailingNonAnchoredQueriesMock() {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.addMockQueries(localSenderChainRid2, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == 0L) createBlockDetail(localSenderChainRid2, listOf(otherLocalSenderMessageBody), "failing-topic") else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("failing-topic") && args["height"] == gtv(-1))
                        gtv(listOf(
                                gtv(mapOf("body" to otherLocalSenderMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })

        QueryProviderMocks.addMockQueries(localSenderChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == 0L) createBlockDetail(localSenderChainRid, listOf(localSenderMessageBody), "my-topic") else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(
                                gtv(mapOf("body" to localSenderMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun globalTopicReceiver() {
        setupClientMocks(listOf(remoteSenderQueryResponse, remoteSenderSecondQueryResponse))
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
                val messages = getTestMessages(node, dappChain)
                assertThat(messages).hasSize(3)
                assertThat(messages.filter { it.sender == remoteSenderChainRid && it.topic == "my-topic" && it.body.contentEquals(remoteSenderEncodedMessageBody) }).hasSize(2)
                assertThat(messages.any { it.sender == localSenderChainRid && it.topic == "my-topic" && it.body.contentEquals(localSenderEncodedMessageBody) }).isTrue()
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
                val messages = getTestMessages(node, dappChain)
                assertThat(messages).hasSize(1)
                val message = messages[0]
                assertThat(message.sender).isEqualTo(remoteSenderChainRid)
                assertThat(message.topic).isEqualTo("my-topic")
                assertThat(message.body.contentEquals(remoteSenderEncodedMessageBody)).isTrue()
            }
        }

        verifyPipesAreEmpty(dappChain)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun interClusterSpecificChainReceiverWithoutAnchoring() {
        setupNonAnchoredClientMocks()

        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_inter_cluster_without_anchoring.xml")!!
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
                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(1)
                val message = messages[0]
                assertThat(message.sender).isEqualTo(remoteSenderChainRid)
                assertThat(message.topic).isEqualTo("my-topic")
                assertThat(message.body.contentEquals(remoteSenderEncodedMessageBody)).isTrue()
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
            val messages = getTestMessages(node, dappChain)
            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(localSenderChainRid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
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
            val messages = getTestMessages(node, dappChain)
            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(localSenderChainRid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
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
                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(1)
                val message = messages[0]
                assertThat(message.sender).isEqualTo(localSenderChainRid)
                assertThat(message.topic).isEqualTo("my-topic")
                assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun anchoringReceiver() {
        QueryProviderMocks.clearMocks()
        QueryProviderMocks.addMockQueries(clusterAnchoringChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = createBlockDetail(clusterAnchoringChainRid, listOf(remoteSenderMessageBody), "my-topic")

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(gtv(mapOf("body" to remoteSenderMessageBody, "height" to gtv(0)))))
                    else
                        gtv(listOf())
        })

        QueryProviderMocks.addMockQueries(systemAnchoringChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) = createBlockDetail(systemAnchoringChainRid, listOf(localSenderMessageBody), "my-topic")

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(gtv(mapOf("body" to localSenderMessageBody, "height" to gtv(0)))))
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
                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(2)
                assertThat(messages.any { it.sender == clusterAnchoringChainRid && it.topic == "my-topic" && it.body.contentEquals(remoteSenderEncodedMessageBody) }).isTrue()
                assertThat(messages.any { it.sender == systemAnchoringChainRid && it.topic == "my-topic" && it.body.contentEquals(localSenderEncodedMessageBody) }).isTrue()
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
        val queryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(messageBody))

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
        val queryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(messageBody, secondMessageBody))

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
                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(2)
                val firstMessage = messages[0]
                assertThat(firstMessage.sender).isEqualTo(remoteSenderChainRid)
                assertThat(firstMessage.topic).isEqualTo("my-topic")
                assertThat(firstMessage.body.contentEquals(encodedMessageBody)).isTrue()

                val secondMessage = messages[1]
                assertThat(secondMessage.sender).isEqualTo(remoteSenderChainRid)
                assertThat(secondMessage.topic).isEqualTo("my-topic")
                assertThat(secondMessage.body.contentEquals(secondEncodedMessageBody)).isTrue()

                assertThat(firstMessage.height).isLessThan(secondMessage.height)
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

        val queryResponse = createQueryResponseForMessage(remoteSenderChainRid, messages)
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
                val nodeMessages = getTestMessages(node, dappChain)
                val messagesPerHeight = nodeMessages.groupingBy { it.height }
                        .eachCount()

                // Expect all messages to be received
                assertThat(nodeMessages).hasSize(messageCount)

                // Expect message distribution over blocks
                assertThat(messagesPerHeight).hasSize(expectedNumberOfBlocks)

                // Expect no block to contain more than messageLimit messages
                assertThat(messagesPerHeight.all { (_, messagesInHeight) -> messagesInHeight <= messageLimit }).isTrue()
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
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(directoryChainBrid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
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
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).isEmpty()
        }

        setupNonAnchoredQueriesMock(messageHeight = 2)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(localSenderChainRid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
            assertThat(message.height).isEqualTo(1)
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
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(localSenderChainRid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
            assertThat(message.height).isEqualTo(0)
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
        awaitChainRestarted(dappChain, 1, skipToHeightConfig.merkleHash(GtvMerkleHashCalculatorV1(cryptoSystem)))

        // skip config should be applied, verify that we don't care about messages at height 1
        setupNonAnchoredQueriesMock(messageHeight = 1, prevMessageHeight = 0)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).hasSize(1)
            val message = messages[0]
            assertThat(message.sender).isEqualTo(localSenderChainRid)
            assertThat(message.topic).isEqualTo("my-topic")
            assertThat(message.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
            assertThat(message.height).isEqualTo(0)
        }

        // See that we read message at height 2
        setupNonAnchoredQueriesMock(messageHeight = 2, prevMessageHeight = 1)
        buildBlock(dappChain)
        for (node in getChainNodes(dappChain)) {
            val messages = getTestMessages(node, dappChain)

            assertThat(messages).hasSize(2)
            messages.forEach {
                assertThat(it.sender).isEqualTo(localSenderChainRid)
                assertThat(it.topic).isEqualTo("my-topic")
                assertThat(it.body.contentEquals(localSenderEncodedMessageBody)).isTrue()
            }
            assertThat(messages[0].height).isEqualTo(0)
            assertThat(messages[1].height).isEqualTo(3)
        }
    }

    @Test
    fun `dapp receiver config - add local topic and read messages`() {

        setupNonAnchoredQueriesMock(messageHeight = 2, topic = "topic-defined-in-config")
        addNonAnchoredQueriesMock(localSenderChainRid2, 2, -1, "L_topic-1", messageBody = gtv("topic-1-message"))
        addNonAnchoredQueriesMock(localSenderChainRid3, 2, -1, "L_topic-2", messageBody = gtv("topic-2-message"))

        startManagedSystem(3, 0)

        val dappChain1 = deployDynamicTopicDappChain()
        buildBlock(dappChain1)

        withReadConnection(nodes[0].postchainContext.blockBuilderStorage, dappChain1) { ctx ->

            val result = dbOperations.loadDappProvidedReceiverTopics(ctx)
            assertThat(result.size).isZero()
        }

        // Add two more topics
        val tx = makeTransaction(getChainNodes(dappChain1)[0], dappChain1, GtxOp(
                "receiver_icmf_update_topics_op",
                gtv(gtv(
                        gtv("L_topic-1"),
                        gtv(localSenderChainRid2.data),
                        gtv(0)
                ), gtv(
                        gtv("L_topic-2"),
                        gtv(localSenderChainRid3.data),
                        gtv(0)
                )),
                gtv(true)
        ))

        buildBlock(dappChain1, tx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain1)

            for (node in getChainNodes(dappChain1)) {
                val messages = getTestMessages(node, dappChain1)

                assertThat(messages).hasSize(3)

                assertThat(messages[0].sender).isEqualTo(localSenderChainRid)
                assertThat(messages[0].topic).isEqualTo("topic-defined-in-config")
                assertThat(messages[0].body.contentEquals(localSenderEncodedMessageBody)).isTrue()

                assertThat(messages[1].sender).isEqualTo(localSenderChainRid2)
                assertThat(messages[1].topic).isEqualTo("L_topic-1")
                assertThat(GtvDecoder.decodeGtv(messages[1].body)).isEqualTo(gtv("topic-1-message"))

                assertThat(messages[2].sender).isEqualTo(localSenderChainRid3)
                assertThat(messages[2].topic).isEqualTo("L_topic-2")
                assertThat(GtvDecoder.decodeGtv(messages[2].body)).isEqualTo(gtv("topic-2-message"))
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `dapp receiver config - add global topic and read messages`() {

        val topic = "G_my-topic"
        val remoteSenderQueryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(remoteSenderMessageBody), topic = topic)
        val remoteSenderSecondQueryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(remoteSenderMessageBody), 1, 0, topic = topic)
        val localSenderQueryResponse = createQueryResponseForMessage(localSenderChainRid, listOf(localSenderMessageBody), topic = topic)

        setupClientMocks(listOf(remoteSenderQueryResponse, remoteSenderSecondQueryResponse), topic = topic)
        setupQueriesMocks(localSenderQueryResponse, topic)

        startManagedSystem(3, 0)

        val dappChain = deployDynamicTopicDappChain(configFile = "/net/postchain/d1/icmf/receiver/blockchain_config_dynamic_topics_no_topic.xml")

        // Add topics
        val addTopicsTx = makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp(
                "receiver_icmf_update_topics_op",
                gtv(gtv(
                        gtv(topic),
                        GtvNull,
                        gtv(0)
                )),
                gtv(true)
        ))

        buildBlock(dappChain, addTopicsTx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {
                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(3)
                assertThat(messages.filter { it.sender == remoteSenderChainRid && it.topic == topic && it.body.contentEquals(remoteSenderEncodedMessageBody) }).hasSize(2)
                assertThat(messages.any { it.sender == localSenderChainRid && it.topic == topic && it.body.contentEquals(localSenderEncodedMessageBody) }).isTrue()
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `dapp receiver config - add global topic with brid and read messages`() {

        val topic = "G_my-topic"
        val remoteSenderQueryResponse = createQueryResponseForMessage(remoteSenderChainRid, listOf(remoteSenderMessageBody), topic = topic)
        setupClientMocks(listOf(remoteSenderQueryResponse), listOf(remoteSenderMessageBody), topic)

        startManagedSystem(3, 0)

        val dappChain = deployDynamicTopicDappChain(configFile = "/net/postchain/d1/icmf/receiver/blockchain_config_dynamic_topics_no_topic.xml")

        // Add topics
        val addTopicsTx = makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp(
                "receiver_icmf_update_topics_op",
                gtv(gtv(
                        gtv(topic),
                        gtv(remoteSenderChainRid.data),
                        gtv(0)
                )),
                gtv(true)
        ))

        buildBlock(dappChain, addTopicsTx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain)
            for (node in getChainNodes(dappChain)) {

                val messages = getTestMessages(node, dappChain)

                assertThat(messages).hasSize(1)
                val message = messages[0]
                assertThat(message.sender).isEqualTo(remoteSenderChainRid)
                assertThat(message.topic).isEqualTo(topic)
                assertThat(message.body.contentEquals(remoteSenderEncodedMessageBody)).isTrue()
            }
        }

        verifyPipesAreEmpty(dappChain)
    }

    /**
     * 1. Add 2 topics
     * 2. Send a message and read it on both topics
     * 3. Remove one topic
     * 2. Send a message on both topics but only receive the active one.
     */
    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `dapp receiver config - remove 1 topic and stop receive messages on that topic`() {
        addNonAnchoredQueriesMock(localSenderChainRid2, 2, -1, "L_topic-1", messageBody = gtv("topic-1-message"))
        addNonAnchoredQueriesMock(localSenderChainRid3, 2, -1, "L_topic-2", messageBody = gtv("topic-2-message"))

        startManagedSystem(3, 0)

        val dappChain1 = deployDynamicTopicDappChain(configFile = "/net/postchain/d1/icmf/receiver/blockchain_config_dynamic_topics_no_topic.xml")
        buildBlock(dappChain1)

        withReadConnection(nodes[0].postchainContext.blockBuilderStorage, dappChain1) { ctx ->
            val result = dbOperations.loadDappProvidedReceiverTopics(ctx)
            assertThat(result.size).isZero()
        }

        // Add two more topics
        val addTopicsTx = makeTransaction(getChainNodes(dappChain1)[0], dappChain1, GtxOp(
                "receiver_icmf_update_topics_op",
                gtv(gtv(
                        gtv("L_topic-1"),
                        gtv(localSenderChainRid2.data),
                        gtv(0)
                ), gtv(
                        gtv("L_topic-2"),
                        gtv(localSenderChainRid3.data),
                        gtv(0)
                )),
                gtv(true)
        ))

        buildBlock(dappChain1, addTopicsTx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain1)

            for (node in getChainNodes(dappChain1)) {
                val messages = getTestMessages(node, dappChain1)

                assertThat(messages).hasSize(2)

                assertThat(messages[0].sender).isEqualTo(localSenderChainRid2)
                assertThat(messages[0].topic).isEqualTo("L_topic-1")
                assertThat(GtvDecoder.decodeGtv(messages[0].body)).isEqualTo(gtv("topic-1-message"))

                assertThat(messages[1].sender).isEqualTo(localSenderChainRid3)
                assertThat(messages[1].topic).isEqualTo("L_topic-2")
                assertThat(GtvDecoder.decodeGtv(messages[1].body)).isEqualTo(gtv("topic-2-message"))
            }
        }

        // New messages
        addNonAnchoredQueriesMock(localSenderChainRid2, 6, 2, "L_topic-1", messageBody = gtv("topic-1-second-message"))
        addNonAnchoredQueriesMock(localSenderChainRid3, 6, 2, "L_topic-2", messageBody = gtv("topic-2-second-message"))

        val removeTopicsTx = makeTransaction(getChainNodes(dappChain1)[0], dappChain1, GtxOp(
                "receiver_icmf_update_topics_op",
                gtv(gtv(
                        gtv("L_topic-2"),
                        gtv(localSenderChainRid3.data),
                        gtv(0)
                )),
                gtv(true)
        ))

        buildBlock(dappChain1, removeTopicsTx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain1)

            for (node in getChainNodes(dappChain1)) {
                val messages = getTestMessages(node, dappChain1)

                assertThat(messages).hasSize(4)
                with (messages[3]) {
                    assertThat(sender).isEqualTo(localSenderChainRid3)
                    assertThat(topic).isEqualTo("L_topic-2")
                    assertThat(GtvDecoder.decodeGtv(body)).isEqualTo(gtv("topic-2-second-message"))
                }
            }
        }

        verifyPipesAreEmpty(dappChain1)
    }

    /**
     * Test add and replace in the same block.
     *
     * In the same block:
     * 1. Add a transaction
     * 2. Replace all transactions
     * 3. Add a transaction
     *
     * This should ignore #1 and create topics for #2 & #3.
     * And then a final step to clear all dapp provided receivers.
     */
    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `dapp receiver config - add, replace and add topics`() {
        addNonAnchoredQueriesMock(localSenderChainRid, 2, -1, "L_topic-1", messageBody = gtv("topic-1-message"))
        addNonAnchoredQueriesMock(localSenderChainRid2, 2, -1, "L_topic-2", messageBody = gtv("topic-2-message"))
        addNonAnchoredQueriesMock(localSenderChainRid3, 2, -1, "L_topic-3", messageBody = gtv("topic-3-message"))

        startManagedSystem(3, 0)

        val dappChain1 = deployDynamicTopicDappChain(configFile = "/net/postchain/d1/icmf/receiver/blockchain_config_dynamic_topics_no_topic.xml")
        buildBlock(dappChain1)

        withReadConnection(nodes[0].postchainContext.blockBuilderStorage, dappChain1) { ctx ->
            val result = dbOperations.loadDappProvidedReceiverTopics(ctx)
            assertThat(result.size).isZero()
        }

        val addTopicsTx = makeTransaction(getChainNodes(dappChain1)[0], dappChain1,
                // Operation #1 - add a topic - this one will be ignored due to the following reset op
                GtxOp("receiver_icmf_update_topics_op",
                        gtv(gtv(gtv("L_topic-1"), gtv(localSenderChainRid.data), gtv(0))),
                        gtv(false)),
                // Operation #2 - replace topics (this will add one topic)
                GtxOp("receiver_icmf_update_topics_op",
                        gtv(gtv(gtv("L_topic-2"), gtv(localSenderChainRid2.data), gtv(0))),
                        gtv(true)),
                // Operation #3 - add another topic
                GtxOp("receiver_icmf_update_topics_op",
                        gtv(gtv(gtv("L_topic-3"), gtv(localSenderChainRid3.data), gtv(0))),
                        gtv(false)),
        )

        buildBlock(dappChain1, addTopicsTx)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            buildBlock(dappChain1)

            for (node in getChainNodes(dappChain1)) {
                val messages = getTestMessages(node, dappChain1)

                assertThat(messages).hasSize(2)

                assertThat(messages[0].sender).isEqualTo(localSenderChainRid2)
                assertThat(messages[0].topic).isEqualTo("L_topic-2")
                assertThat(GtvDecoder.decodeGtv(messages[0].body)).isEqualTo(gtv("topic-2-message"))

                assertThat(messages[1].sender).isEqualTo(localSenderChainRid3)
                assertThat(messages[1].topic).isEqualTo("L_topic-3")
                assertThat(GtvDecoder.decodeGtv(messages[1].body)).isEqualTo(gtv("topic-3-message"))
            }
        }

        withReadConnection(nodes[0].postchainContext.blockBuilderStorage, dappChain1) { ctx ->
            val result = dbOperations.loadDappProvidedReceiverTopics(ctx)
            assertThat(result.size).isEqualTo(2)
            with (result[0]) {
                assertThat(topic).isEqualTo("L_topic-2")
                assertThat(bcRid.contentEquals(localSenderChainRid2.data)).isTrue()
                assertThat(skipToHeight).isEqualTo(0)
            }
            with (result[1]) {
                assertThat(topic).isEqualTo("L_topic-3")
                assertThat(bcRid.contentEquals(localSenderChainRid3.data)).isTrue()
                assertThat(skipToHeight).isEqualTo(0)
            }
        }

        val removeTopicsTx = makeTransaction(getChainNodes(dappChain1)[0], dappChain1,
                // Operation #1 - add a topic - this one will be ignored due to the following reset op
                GtxOp("receiver_icmf_update_topics_op", GtvArray(listOf<Gtv>().toTypedArray()), gtv(true)),
        )

        buildBlock(dappChain1, removeTopicsTx)

        withReadConnection(nodes[0].postchainContext.blockBuilderStorage, dappChain1) { ctx ->
            val result = dbOperations.loadDappProvidedReceiverTopics(ctx)
            assertThat(result.size).isZero()
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
            receiverSpecialTxExtension.anchoredReceivers.forEach {
                it.getRelevantPipes().filterIsInstance<QueuedPipe>().forEach { pipe ->
                    assertThat(pipe.queueLength).isEqualTo(0)
                    assertThat(pipe.queueSizeBytes).isEqualTo(0)
                }
            }
            receiverSpecialTxExtension.nonAnchoredReceivers.forEach {
                it.getRelevantPipes().filterIsInstance<QueuedPipe>().forEach { pipe ->
                    assertThat(pipe.queueLength).isEqualTo(0)
                    assertThat(pipe.queueSizeBytes).isEqualTo(0)
                }
            }
        }
    }

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, messageBodies: List<Gtv>, messageHeight: Long = 0, prevMessageHeight: Long = -1, topic: String = "my-topic"): Gtv {
        val blockDetail = createBlockDetail(blockchainRid, messageBodies, topic, messageHeight, prevMessageHeight)
        return gtv(
                mapOf(
                        "block_header" to gtv(blockDetail.header),
                        "witness" to gtv(blockDetail.witness),
                        "anchor_height" to gtv(messageHeight)
                )
        )
    }

    private fun createBlockDetail(blockchainRid: BlockchainRid, messageBodies: List<Gtv>, topic: String, messageHeight: Long = 0, prevMessageHeight: Long = -1): BlockDetail {
        val hashCalculator = GtvMerkleHashCalculatorV1(cryptoSystem)
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

    private fun buildAnchorHeader(icmfHeaders: List<ByteArray>, prevHeight: Long = -1, topic: String = "my-topic"): BlockDetail {
        val hashCalculator = GtvMerkleHashCalculatorV1(cryptoSystem)
        val icmfBlockRids = icmfHeaders.map {
            val decodedHeader = BlockHeaderData.fromBinary(it)
            val blockRid = decodedHeader.toGtv().merkleHash(hashCalculator)
            gtv(blockRid)
        }

        val blockHeader = BlockHeaderData(
                gtv(clusterAnchoringChainRid.data),
                gtv(clusterAnchoringChainRid.data),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(prevHeight + 1),
                GtvNull,
                gtv(
                        mapOf(
                                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                                        topic to TopicHeaderData(gtv(icmfBlockRids).merkleHash(hashCalculator), prevHeight).toGtv()
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
                clusterAnchoringChainRid.data.wrap(),
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

    private fun deployDynamicTopicDappChain(
            configFile: String = "/net/postchain/d1/icmf/receiver/blockchain_config_dynamic_topics.xml",
            additionRellCode: String = "",
            modifyConfig: (String) -> String = { it }
    ) =
        deployDappChain(
                configFile = configFile,
                additionRellCode =
                """
                    import .receiver.*;
                    operation receiver_icmf_update_topics_op(topics: list<icmf_receiver_topic>, replace: boolean) {
                        receiver_icmf_update_topics(topics, replace);
                    }
                """.trimIndent() + additionRellCode,
        ) {
            modifyConfig(it).replace("operation __icmf_message", "operation disabled__icmf_message")
        }


    private fun getTestMessages(node: PostchainTestNode, chainId: Long): List<TestMessage> {

        return withReadConnection(node.postchainContext.blockBuilderStorage, chainId) { ctx ->
            DatabaseAccess.of(ctx).let { da ->
                val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                jooq.select()
                        .from(da.tableName(ctx, testMessageTable))
                        .fetch()
                        .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }
            }
        }
    }
}
