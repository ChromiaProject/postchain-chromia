package net.postchain.d1.icmf

import assertk.assert
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
import net.postchain.client.core.PostchainReadClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchor.ICMF_ANCHOR_HEADERS_EXTRA
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
import org.apache.logging.log4j.test.appender.ListAppender
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

    private val anchorChainRid = BlockchainRid.buildRepeat(0)

    private val senderOneChainRid = BlockchainRid.buildRepeat(1)
    private val senderOneMessageBody = gtv("hej1")
    private val senderOneEncodedMessageBody = GtvEncoder.encodeGtv(senderOneMessageBody)
    private val senderOneQueryResponse = createQueryResponseForMessage(senderOneChainRid, listOf(senderOneMessageBody))

    private val senderTwoChainRid = BlockchainRid.buildRepeat(2)
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
        MockPostchainRestApi.addMockClient(anchorChainRid, mock {
            on { blockAtHeightSync(0L) } doReturn buildAnchorHeader(listOf(anchorQueryResponse["block_header"]!!.asByteArray()))
            on {
                querySync(
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
                querySync(
                        "icmf_get_messages", gtv(
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

        QueryProviderMocks.anchorQueries = object : PostchainReadClient {
            override fun blockAtHeightSync(height: Long) =
                    buildAnchorHeader(listOf(senderTwoQueryResponse["block_header"]!!.asByteArray()))

            override fun currentBlockHeightSync(): Long = throw NotImplementedError()

            override fun querySync(name: String, gtv: Gtv): Gtv =
                    if (name == "icmf_get_headers_with_messages_after_height" && gtv["topic"] == gtv("my-topic") && gtv["from_anchor_height"] == gtv(
                                    -1
                            )
                    )
                        gtv(listOf(senderTwoQueryResponse))
                    else if (name == "icmf_get_headers_with_messages_after_height")
                        gtv(listOf())
                    else
                        GtvNull
        }

        QueryProviderMocks.addMockQueries(senderTwoChainRid) { name, args ->
            if (name == "icmf_get_messages" && args["topic"] == gtv("my-topic") && args["height"] == gtv(0))
                gtv(listOf(senderTwoMessageBody))
            else
                GtvNull
        }
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
            for (node in dappChain.nodes()) {
                withReadConnection(node.postchainContext.storage, dappChain.chain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assert(messages).hasSize(2)
                        assert(messages.any { it.sender == senderOneChainRid && it.topic == "my-topic" && it.body.contentEquals(senderOneEncodedMessageBody) }).isTrue()
                        assert(messages.any { it.sender == senderTwoChainRid && it.topic == "my-topic" && it.body.contentEquals(senderTwoEncodedMessageBody) }).isTrue()
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
            for (node in dappChain.nodes()) {
                withReadConnection(node.postchainContext.storage, dappChain.chain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assert(messages).hasSize(1)
                        val message = messages[0]
                        assert(message.sender).isEqualTo(senderOneChainRid)
                        assert(message.topic).isEqualTo("my-topic")
                        assert(message.body.contentEquals(senderOneEncodedMessageBody)).isTrue()
                    }
                }
            }
        }

        verifyPipesAreEmpty(dappChain)
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
            for (node in dappChain.nodes()) {
                withReadConnection(node.postchainContext.storage, dappChain.chain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assert(messages).hasSize(1)
                        val message = messages[0]
                        assert(message.sender).isEqualTo(senderTwoChainRid)
                        assert(message.topic).isEqualTo("my-topic")
                        assert(message.body.contentEquals(senderTwoEncodedMessageBody)).isTrue()
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun maxMessageSize() {
        val context = LoggerContext.getContext(false)
        val logger = context.getLogger(ClusterGlobalTopicPipe::class.java)
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
            assert(appender.events.map { it.message.toString() })
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
            for (node in dappChain.nodes()) {
                withReadConnection(node.postchainContext.storage, dappChain.chain) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        val jooq = DSL.using(ctx.conn, SQLDialect.POSTGRES)
                        val messages = jooq.select()
                                .from(tableName(ctx, testMessageTable))
                                .fetch()
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY], it[COLUMN_HEIGHT]) }

                        assert(messages).hasSize(2)
                        val firstMessage = messages[0]
                        assert(firstMessage.sender).isEqualTo(senderOneChainRid)
                        assert(firstMessage.topic).isEqualTo("my-topic")
                        assert(firstMessage.body.contentEquals(encodedMessageBody)).isTrue()

                        val secondMessage = messages[1]
                        assert(secondMessage.sender).isEqualTo(senderOneChainRid)
                        assert(secondMessage.topic).isEqualTo("my-topic")
                        assert(secondMessage.body.contentEquals(secondEncodedMessageBody)).isTrue()

                        assert(firstMessage.height).isLessThan(secondMessage.height)
                    }
                }
            }
        }

        verifyPipesAreEmpty(dappChain)
    }

    private fun verifyPipesAreEmpty(dappChain: NodeSet) {
        // Let all nodes be primary once, so they can clean their pipes
        repeat(nodes.size) {
            buildBlock(dappChain)
        }

        nodes.forEach { node ->
            val receiverGTXModule = node.getModules(1L).find { it is IcmfReceiverGTXModule }!!
            val receiverSpecialTxExtension = receiverGTXModule.getSpecialTxExtensions()[0] as IcmfReceiverSpecialTxExtension
            val globalTopicPipe = receiverSpecialTxExtension.receivers[0].getRelevantPipes()[0] as ClusterGlobalTopicPipe

            assert(globalTopicPipe.queueIsEmpty).isTrue()
        }
    }

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, messageBodies: List<Gtv>): Gtv {
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
                                        "my-topic" to TopicHeaderData(
                                                gtv(listOf(gtv(messageBodies.map { gtv(it.merkleHash(hashCalculator)) }))).merkleHash(hashCalculator),
                                                -1L
                                        ).toGtv()
                                )
                        )
                )
        ).toGtv()
        val blockRid = blockHeader.merkleHash(hashCalculator)
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(
                        cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(blockRid)
                )
        ).getRawData()
        return gtv(
                mapOf(
                        "block_header" to gtv(GtvEncoder.encodeGtv(blockHeader)),
                        "witness" to gtv(rawWitness),
                        "anchor_height" to gtv(0)
                )
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
                gtv(anchorChainRid.data),
                gtv(anchorChainRid.data),
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
                blockRid,
                anchorChainRid.data,
                GtvEncoder.encodeGtv(blockHeader),
                0L,
                listOf(),
                rawWitness,
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
