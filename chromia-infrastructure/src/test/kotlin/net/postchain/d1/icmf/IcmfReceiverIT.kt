package net.postchain.d1.icmf

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainBlockClient
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

class IcmfReceiverIT : ManagedModeTest() {

    private val systemAnchoringChain = BlockchainRid.buildRepeat(0)
    private val clusterAnchoringChain = BlockchainRid.buildRepeat(1)

    private val senderOneChainRid = BlockchainRid.buildRepeat(2)
    private val senderOneMessageBody = gtv("hej1")
    private val senderOneEncodedMessageBody = GtvEncoder.encodeGtv(senderOneMessageBody)
    private val senderOneQueryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(senderOneMessageBody))

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

    private fun setupClientMocks(anchorQueryResponse: Gtv = senderOneQueryResponse, messageQueryResponse: List<Gtv> = listOf(senderOneMessageBody)) {
        MockPostchainRestApi.addMockClient(clusterAnchoringChain, mock {
            on { blockAtHeight(0L) } doReturn buildAnchorHeader(listOf(anchorQueryResponse["block_header"]!!.asByteArray()))
            on {
                query(
                        "icmf_get_headers_with_messages_after_height", gtv(
                        mapOf(
                                "topic" to gtv("my-topic"),
                                "from_anchor_height" to gtv(-1)
                        )
                )
                )
            } doReturn gtv(listOf(anchorQueryResponse))
        })

        MockPostchainRestApi.addMockClient(senderOneChainRid, mock {
            on {
                query(
                        QUERY_ICMF_GET_MESSAGES_AT_HEIGHT, gtv(
                        mapOf(
                                "topic" to gtv("my-topic"),
                                "height" to gtv(0)
                        )
                )
                )
            } doReturn gtv(messageQueryResponse)
        })
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

    private fun setupNonAnchoredQueriesMock() {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.addMockQueries(senderTwoChainRid, object : PostchainBlockClient {
            override fun blockAtHeight(height: Long) =
                    if (height == 0L) createBlockDetail(senderTwoChainRid, listOf(senderTwoMessageBody), "my-topic") else null

            override fun query(name: String, args: Gtv) =
                    if (name == QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT && args["topic"] == gtv("my-topic") && args["height"] == gtv(-1))
                        gtv(listOf(gtv(mapOf("body" to senderTwoMessageBody, "height" to gtv(0)))))
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
        setupClientMocks()
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

                        assertThat(messages).hasSize(2)
                        assertThat(messages.any { it.sender == senderOneChainRid && it.topic == "my-topic" && it.body.contentEquals(senderOneEncodedMessageBody) }).isTrue()
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

        setupClientMocks(queryResponse, listOf(messageBody))

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

        setupClientMocks(queryResponse, listOf(messageBody, secondMessageBody))

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

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, messageBodies: List<Gtv>): Gtv {
        val blockDetail = createBlockDetail(blockchainRid, messageBodies, "my-topic")
        return gtv(
                mapOf(
                        "block_header" to gtv(blockDetail.header),
                        "witness" to gtv(blockDetail.witness),
                        "anchor_height" to gtv(0)
                )
        )
    }

    private fun createBlockDetail(blockchainRid: BlockchainRid, messageBodies: List<Gtv>, topic: String): BlockDetail {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val blockHeader = BlockHeaderData(
                gtv(blockchainRid.data),
                gtv(blockchainRid.data),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(0),
                GtvNull,
                gtv(
                        mapOf(
                                ICMF_BLOCK_HEADER_EXTRA to gtv(
                                        topic to TopicHeaderData(
                                                gtv(listOf(gtv(messageBodies.map { gtv(it.merkleHash(hashCalculator)) }))).merkleHash(hashCalculator),
                                                -1L
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
                0,
                listOf(),
                rawWitness.wrap(),
                blockHeader.getTimestamp()
        )
    }

    private fun buildAnchorHeader(icmfHeaders: List<ByteArray>): BlockDetail {
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
                gtv(0),
                GtvNull,
                gtv(
                        mapOf(
                                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                                        "my-topic" to TopicHeaderData(gtv(icmfBlockRids).merkleHash(hashCalculator), -1L).toGtv()
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
