package net.postchain.d1.icmf

import assertk.assert
import assertk.assertions.containsExactly
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
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
    private val senderOneQueryResponse = createQueryResponseForMessage(senderOneChainRid, senderOneEncodedMessageBody)

    private val senderTwoChainRid = BlockchainRid.buildRepeat(2)
    private val senderTwoMessageBody = gtv("hej2")
    private val senderTwoEncodedMessageBody = GtvEncoder.encodeGtv(senderTwoMessageBody)
    private val senderTwoQueryResponse = createQueryResponseForMessage(senderTwoChainRid, senderTwoEncodedMessageBody)

    @BeforeEach
    fun setup() {
        PostchainClientMocks.clearMocks()

        PostchainClientMocks.addMockClient(anchorChainRid, mock {
            on { currentBlockHeightSync() } doReturn 1L
            on {
                querySync("icmf_get_headers_with_messages_between_heights", gtv(mapOf(
                        "topic" to gtv("my-topic"),
                        "from_anchor_height" to gtv(0),
                        "to_anchor_height" to gtv(1)
                )))
            } doReturn gtv(listOf(senderOneQueryResponse, senderTwoQueryResponse))
        })

        PostchainClientMocks.addMockClient(senderOneChainRid, mock {
            on {
                querySync("icmf_get_messages", gtv(mapOf(
                        "topic" to gtv("my-topic"),
                        "height" to gtv(0)
                )))
            } doReturn gtv(listOf(senderOneMessageBody))
        })

        PostchainClientMocks.addMockClient(senderTwoChainRid, mock {
            on {
                querySync("icmf_get_messages", gtv(mapOf(
                        "topic" to gtv("my-topic"),
                        "height" to gtv(0)
                )))
            } doReturn gtv(listOf(senderTwoMessageBody))
        })
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun globalTopicReceiver() {
        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_global_1.xml")!!.readText())

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig))

        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
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
                                TestMessage(senderTwoChainRid, "my-topic", senderTwoEncodedMessageBody)
                        )
                    }
                }
            }
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun specificChainReceiver() {
        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/receiver/blockchain_config_specific_1.xml")!!.readText())

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig))

        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
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

    private fun createQueryResponseForMessage(blockchainRid: BlockchainRid, encodedMessageBody: ByteArray): Gtv {
        val blockHeader = BlockHeaderData(
                gtv(blockchainRid.data),
                gtv(blockchainRid.data),
                gtv(ByteArray(32)),
                gtv(0),
                gtv(0),
                GtvNull,
                gtv(mapOf(
                        ICMF_BLOCK_HEADER_EXTRA to gtv(
                                "my-topic" to TopicHeaderData.fromMessageHashes(
                                        listOf(cryptoSystem.digest(encodedMessageBody)),
                                        cryptoSystem,
                                        -1L).toGtv()
                        )
                ))
        ).toGtv()
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))).getRawData()
        return ClusterGlobalTopicPipe.SignedBlockHeaderWithAnchorHeight(
                GtvEncoder.encodeGtv(blockHeader),
                rawWitness,
                0
        ).toGtv()
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
