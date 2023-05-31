package net.postchain.d1.icmf

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.RELL_SOURCE_PATH
import net.postchain.d1.TopicHeaderData
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

class IcmfSenderIT : ManagedModeTest() {

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun icmfHappyPath() {
        startManagedSystem(3, 0)

        val icmfTestCode = File(RELL_SOURCE_PATH, "messaging/icmf.rell").readText() +
                """
                    operation test_message(text) {
                        send_message("L_my-topic", text.to_gtv());
                    }
                """
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/sender/blockchain_config_1.xml")!!.readText(),
                mapOf("messaging.icmf" to gtv(icmfTestCode), getIcmfConstantsCode()))

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = IcmfTestBlockchainConfigurationFactory())

        // Messages in block 0
        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.mapIndexed { index, message ->
            makeTransaction(getChainNodes(dappChain)[0], dappChain, index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())

        verifyMessages(dappChain, 0, "L_my-topic", -1, block0Messages, block0Messages)

        // No messages in block 1
        buildBlock(dappChain, 1)

        // Messages in block 2
        val block2Messages = listOf("test2", "test3")
        val block2Txs = block2Messages.mapIndexed { index, message ->
            makeTransaction(getChainNodes(dappChain)[0], dappChain, block0Messages.size + index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain, 2, *block2Txs.toTypedArray())

        // Expecting previous height to be 0
        verifyMessages(dappChain, 2, "L_my-topic", 0, block2Messages, block0Messages + block2Messages)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun icmfTooBigMessage() {
        startManagedSystem(3, 0)

        val icmfTestCode = File(RELL_SOURCE_PATH, "messaging/icmf.rell").readText() +
                """
                    operation test_message(text) {
                        send_message("L_my-topic", text.to_gtv());
                    }
                """
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/sender/blockchain_config_1.xml")!!.readText(),
                mapOf("messaging.icmf" to gtv(icmfTestCode), getIcmfConstantsCode()))

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = IcmfTestBlockchainConfigurationFactory())

        val message = "imtoobig".repeat(2 * 1024 * 1024)
        val tx = makeTransaction(getChainNodes(dappChain)[0], dappChain, 0, GtxOp("test_message", gtv(message)))
        buildBlock(dappChain, 0, tx)
        val txStatus = getChainNodes(dappChain)[0].getBlockchainInstance(1L).blockchainEngine.getTransactionQueue().getTransactionStatus(tx.getHash())
        assertThat(txStatus).isEqualTo(TransactionStatus.REJECTED)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `icmf should not add message to header with not allowed topic`() {
        startManagedSystem(3, 0)

        val icmfTestCode = File(RELL_SOURCE_PATH, "messaging/icmf.rell").readText() +
                """
                    operation test_message(text) {
                        send_message("my-topic", text.to_gtv());
                    }
                """
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/sender/blockchain_config_1.xml")!!.readText(),
                mapOf("messaging.icmf" to gtv(icmfTestCode), getIcmfConstantsCode()))

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = IcmfTestBlockchainConfigurationFactory())

        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.mapIndexed { index, message ->
            makeTransaction(getChainNodes(dappChain)[0], dappChain, index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())
        verifyMessagesMissing(dappChain, 0)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun `icmf should not add message to header for normal chain with global topic`() {
        startManagedSystem(3, 0)

        val icmfTestCode = File(RELL_SOURCE_PATH, "messaging/icmf.rell").readText() +
                """
                    operation test_message(text) {
                        send_message("G_my-topic", text.to_gtv());
                    }
                """
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/icmf/sender/blockchain_config_1.xml")!!.readText(),
                mapOf("messaging.icmf" to gtv(icmfTestCode), getIcmfConstantsCode()))

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = IcmfTestBlockchainConfigurationFactory())

        val block0Messages = listOf("test0", "test1")
        val block0Txs = block0Messages.mapIndexed { index, message ->
            makeTransaction(getChainNodes(dappChain)[0], dappChain, index, GtxOp("test_message", gtv(message)))
        }
        buildBlock(dappChain, 0, *block0Txs.toTypedArray())
        verifyMessagesMissing(dappChain, 0)
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
                val blockRid = blockQueries.getBlockRid(height).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
                val expectedHash = gtv(expectedMessages.map { message -> gtv(gtv(message).merkleHash(hashCalculator)) })
                        .merkleHash(hashCalculator)

                val topicHeader = TopicHeaderData.fromGtv(decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asDict()[topic]!!)
                assertThat(topicHeader.hash.wrap()).isEqualTo(expectedHash.wrap())

                assertThat(topicHeader.previousBlockHeight).isEqualTo(expectedPreviousMessageBlockHeight)

                val dbOps = IcmfDatabaseOperationsImpl()

                val allMessages = dbOps.getSentMessagesAfterHeight(it, topic, -1)
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
                val blockRid = blockQueries.getBlockRid(height).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderData.fromBinary(blockHeader.rawData)
                assertThat(decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]).isNull()
            }
        }
    }

    private fun makeTransaction(node: PostchainTestNode, chainId: Long, id: Int, op: GtxOp): IcmfTestTransaction {
        val operations = arrayOf(op)
        return IcmfTestTransaction(
                id,
                {
                    node.getModules(chainId).find { it.javaClass.simpleName.startsWith("Rell") }!!.makeTransactor(
                            ExtOpData.build(
                                    op.asOpData(),
                                    0,
                                    GtxBody(ChainUtil.ridOf(chainId), operations, arrayOf()),
                                    operations.map { it.asOpData() }.toTypedArray()
                            )
                    )
                }
        )
    }

    class IcmfTestTransaction(id: Int, private val makeTransactor: () -> Transactor, good: Boolean = true, correct: Boolean = true) :
            TestTransaction(id, good, correct) {
        override fun apply(ctx: TxEContext): Boolean {
            val op = makeTransactor()
            op.checkCorrectness()
            op.apply(ctx)
            return true
        }
    }

    private fun getIcmfConstantsCode(): Pair<String, Gtv> {
        return "messaging.icmf_constants" to gtv(File(RELL_SOURCE_PATH, "messaging/icmf_constants.rell").readText())
    }
}
