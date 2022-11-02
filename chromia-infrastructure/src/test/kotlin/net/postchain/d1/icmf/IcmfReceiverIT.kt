package net.postchain.d1.icmf

import assertk.assert
import assertk.assertions.containsAll
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_SENDER
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.COLUMN_TOPIC
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.testMessageTable
import net.postchain.devtools.ManagedModeTest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.jooq.SQLDialect
import org.jooq.impl.DSL
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
    private val senderOneQueryResponse = createQueryResponseForMessage(senderOneChainRid, senderOneMessageBody)

    private val senderTwoChainRid = BlockchainRid.buildRepeat(2)
    private val senderTwoMessageBody = gtv("hej2")
    private val senderTwoEncodedMessageBody = GtvEncoder.encodeGtv(senderTwoMessageBody)
    private val senderTwoQueryResponse = createQueryResponseForMessage(senderTwoChainRid, senderTwoMessageBody)

    private fun setupClientMocks() {
        PostchainClientMocks.clearMocks()

        PostchainClientMocks.addMockClient(anchorChainRid, mock {
            on { currentBlockHeightSync() } doReturn 1L
            on { blockAtHeightSync(0L) } doReturn buildAnchorHeader(listOf(senderOneQueryResponse["block_header"]!!.asByteArray()))
            on {
                querySync(
                        "icmf_get_headers_with_messages_after_height", gtv(
                        mapOf(
                                "topic" to gtv("my-topic"),
                                "from_anchor_height" to gtv(-1)
                        )
                )
                )
            } doReturn gtv(listOf(senderOneQueryResponse))
        })

        PostchainClientMocks.addMockClient(senderOneChainRid, mock {
            on {
                querySync(
                        "icmf_get_messages", gtv(
                        mapOf(
                                "topic" to gtv("my-topic"),
                                "height" to gtv(0)
                        )
                )
                )
            } doReturn gtv(listOf(senderOneMessageBody))
        })
    }

    private fun setupQueriesMocks() {
        QueryProviderMocks.clearMocks()

        QueryProviderMocks.anchorQueries = object : PostchainReadClient {
            override fun blockAtHeightSync(height: Long) =
                    buildAnchorHeader(listOf(senderTwoQueryResponse["block_header"]!!.asByteArray()))

            override fun currentBlockHeightSync(): Long = 1

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
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY]) }

                        assert(messages.size).isEqualTo(2)
                        assert(messages).containsAll(
                                TestMessage(senderOneChainRid, "my-topic", senderOneEncodedMessageBody),
                                TestMessage(senderTwoChainRid, "my-topic", senderTwoEncodedMessageBody)
                        )
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
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY]) }

                        assert(messages).containsExactly(
                                TestMessage(senderOneChainRid, "my-topic", senderOneEncodedMessageBody),
                        )
                    }
                }
            }
        }
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
                                .map { TestMessage(BlockchainRid(it[COLUMN_SENDER]), it[COLUMN_TOPIC], it[COLUMN_BODY]) }

                        assert(messages).containsExactly(
                                TestMessage(senderTwoChainRid, "my-topic", senderTwoEncodedMessageBody),
                        )
                    }
                }
            }
        }
    }

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, messageBody: Gtv): Gtv {
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
                                                gtv(listOf(messageBody)).merkleHash(GtvMerkleHashCalculator(cryptoSystem)),
                                                -1L
                                        ).toGtv()
                                )
                        )
                )
        ).toGtv()
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculator(cryptoSystem))
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

    data class TestMessage(
            val sender: BlockchainRid,
            val topic: String,
            val body: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TestMessage

            if (sender != other.sender) return false
            if (topic != other.topic) return false
            if (!body.contentEquals(other.body)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + topic.hashCode()
            result = 31 * result + body.contentHashCode()
            return result
        }
    }
}
