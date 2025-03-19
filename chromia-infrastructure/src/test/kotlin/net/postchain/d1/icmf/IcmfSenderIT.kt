package net.postchain.d1.icmf

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.d1.TopicHeaderData
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GtxOp
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class IcmfSenderIT : IcmfBaseIT() {

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun icmfHappyPath() {
        startManagedSystem(3, 0)
        val topic = "L_my-topic"
        val dappChain = deployDappChain()

        // Messages in block 0
        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.map {
            makeTransaction(getChainNodes(dappChain).first(), dappChain, GtxOp("test_message", gtv(topic), gtv(it)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())
        verifyMessages(dappChain, 0, topic, -1, block0Messages, block0Messages)

        // No messages in block 1
        buildBlock(dappChain, 1)

        // Messages in block 2
        val block2Messages = listOf("test2", "test3")
        val block2Txs = block2Messages.map {
            makeTransaction(getChainNodes(dappChain).first(), dappChain, GtxOp("test_message", gtv(topic), gtv(it)))
        }
        buildBlock(dappChain, 2, *block2Txs.toTypedArray())
        // Expecting previous height to be 0
        verifyMessages(dappChain, 2, topic, 0, block2Messages, block0Messages + block2Messages)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun icmfTooBigMessage() {
        startManagedSystem(3, 0)
        val topic = "L_my-topic"
        val dappChain = deployDappChain()

        val message = "imtoobig".repeat(2 * 1024 * 1024)
        val tx = makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp("test_message", gtv(topic), gtv(message)))
        buildBlock(dappChain, 0, tx)
        await.atMost(Duration.ONE_MINUTE).untilAsserted {
            val txStatuses = getChainNodes(dappChain).map { it.transactionQueue().getTransactionStatus(tx.getRID()) }
            assertThat(txStatuses).any { it.isEqualTo(TransactionStatus.REJECTED) }
            val txRejectReasons = getChainNodes(dappChain).mapNotNull { it.transactionQueue().getRejectionReason(tx.getRID().wrap())?.first?.message }
            assertThat(txRejectReasons.any { it.contains("Message body too big") }).isTrue()
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `icmf should not add message to header with not allowed topic`() {
        startManagedSystem(3, 0)
        val topic = "my-topic"
        val dappChain = deployDappChain()

        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.map {
            makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp("test_message", gtv(topic), gtv(it)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())
        verifyMessagesMissing(dappChain, 0)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `icmf should not add message to header for normal chain with global topic`() {
        startManagedSystem(3, 0)
        val topic = "G_my-topic"
        val dappChain = deployDappChain()

        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.map {
            makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp("test_message", gtv(topic), gtv(it)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())
        verifyMessagesMissing(dappChain, 0)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `message query limit is respected`() {
        startManagedSystem(3, 0)
        val topic = "L_my-topic"

        // Query limit is 3
        val dappChain = deployDappChain(configFile = "/icmf/sender_with_query_limit.xml")

        // Send 2 msgs per block
        for (i in 0..2) {
            val blockMessages = listOf("test_first_$i", "test_second_$i")
            val blockTxs = blockMessages.map {
                makeTransaction(getChainNodes(dappChain)[0], dappChain, GtxOp("test_message", gtv(topic), gtv(it)))
            }
            buildBlock(dappChain, i.toLong(), *blockTxs.toTypedArray())
        }

        val messages = nodes[0].getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
                .query(QUERY_ICMF_GET_MESSAGES_AFTER_HEIGHT, gtv(mapOf("topic" to gtv(topic), "height" to gtv(-1))))
                .get()
                .asArray()

        // Ensure limit is not cutting off in the middle of a height
        assertThat(messages).hasSize(4)
        assertThat(messages.filter { it["height"]!!.asInteger() == 0L }).hasSize(2)
        assertThat(messages.filter { it["height"]!!.asInteger() == 1L }).hasSize(2)
        assertThat(messages.filter { it["height"]!!.asInteger() == 2L }).hasSize(0)
    }

    private fun verifyMessages(dappChain: Long,
                               height: Long,
                               topic: String,
                               expectedPreviousMessageBlockHeight: Long,
                               expectedMessages: List<String>,
                               expectedAllMessages: List<String>
    ) {
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) {
                val blockQueries = node.getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
                val blockHeader = blockQueries.getBlockAtHeight(height).get()!!.header
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                val hashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)
                val expectedHash = gtv(expectedMessages.map { message -> gtv(gtv(message).merkleHash(hashCalculator)) })
                        .merkleHash(hashCalculator)

                val topicHeader = TopicHeaderData.fromGtv(decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()[topic]!!)
                assertThat(topicHeader.hash.wrap()).isEqualTo(expectedHash.wrap())

                assertThat(topicHeader.previousBlockHeight).isEqualTo(expectedPreviousMessageBlockHeight)

                val dbOps = IcmfDatabaseOperationsImpl()

                val allMessages = dbOps.getSentMessagesAfterHeight(it, topic, -1, DEFAULT_MESSAGE_QUERY_LIMIT)
                assertThat(allMessages.size).isEqualTo(expectedAllMessages.size)
                expectedAllMessages.forEachIndexed { index, expectedMessage ->
                    assertThat(allMessages[index].body.asString()).isEqualTo(expectedMessage)
                }

                val messages = dbOps.getSentMessagesAtHeight(it, topic, height)
                assertThat(messages.size).isEqualTo(expectedMessages.size)
                expectedMessages.forEachIndexed { index, expectedMessage ->
                    assertThat(messages[index].asString()).isEqualTo(expectedMessage)
                }
            }
        }
    }

    private fun verifyMessagesMissing(dappChain: Long, height: Long) {
        for (node in getChainNodes(dappChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, dappChain) {
                val blockQueries = node.getBlockchainInstance(dappChain).blockchainEngine.getBlockQueries()
                val blockHeader = blockQueries.getBlockAtHeight(height).get()!!.header
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                assertThat(decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]).isNull()
            }
        }
    }
}
