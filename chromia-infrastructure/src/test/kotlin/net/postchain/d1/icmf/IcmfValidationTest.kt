package net.postchain.d1.icmf

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.core.BlockDetail
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchorHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchoredHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageHashOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock

class IcmfValidationTest {
    private val cluster = IcmfTestClusterManagement.senderCluster
    private val topic = "my-topic"
    private val secondTopic = "my-second-topic"
    private val irrelevantTopic = "irrelevant-topic"
    private val anchorBlockchainRID = BlockchainRid.buildRepeat(0)
    private val blockchainRID = BlockchainRid.buildRepeat(1)
    private val irrelevantBlockchainRID = BlockchainRid.buildRepeat(2)
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val hashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)
    private val chainID: Long = 1
    private val spilledMessage = gtv("hej")
    private val specialTxSizeMargin = 100 * 1024L
    private val messageLimit = 100L
    private val maxMessageDelay = 1000L
    private val defaultIcmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverTopicsAndSpecificBlockchainConfig(listOf(topic, secondTopic), null), null, null, null, null, null, null, specialTxSizeMargin, 10, maxMessageDelay)

    private val mockModule: GTXModule = mock {}
    private val mockContext: BlockEContext = mock {}
    private val dbMock: IcmfReceiverDatabaseOperations = mock {
        on { loadLastMessageHeight(mockContext, blockchainRID, topic) } doReturn -1L
        on { loadLastAnchoredHeight(mockContext, cluster, topic) } doReturn -1L
        on { loadLastAnchoredHeight(mockContext, cluster, irrelevantTopic) } doReturn -1L
        on { loadOldestSpilledMessage(mockContext, blockchainRID, topic) } doReturn SpilledMessage(
                0,
                spilledMessage.merkleHash(hashCalculator),
                cluster,
                0,
                2
        )
        on { loadSpilledMessageCounts(mockContext, cluster, 0, topic) } doReturn mapOf()
        on { loadSpilledMessageCounts(mockContext, cluster, 0, secondTopic) } doReturn mapOf()
    }

    @Test
    fun success() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun invalidParameters() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val anchoredHeaderOp = OpData(AnchoredHeaderOp.OP_NAME, arrayOf(
                GtvNull,
                GtvNull
        ))

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(anchoredHeaderOp))
        }.isInstanceOf<UserMistake>().messageContains("Invalid operation")
    }

    @Test
    fun invalidOperationOrder() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(ops[0], ops[2], ops[1]))
        }.isInstanceOf<UserMistake>().messageContains("got __anchored_icmf_message_hash before any __anchored_icmf_header or __non_anchored_icmf_header")
    }

    @Test
    fun invalidMessageSignature() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val invalidSigner = cryptoSystem.generateKeyPair()
        val ops = createOpData(
                listOf(),
                -1,
                messageSigner = invalidSigner,
                anchorSigner = IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("Unable to extract TopicHeaderData")
    }

    @Test
    fun missingExtraHeader() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1,
                messageExtraDataOverride = mapOf()
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("Unable to extract TopicHeaderData")
    }

    @Test
    fun missingExtraHeaderTopicData() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1,
                messageExtraDataOverride = mapOf(
                        ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf())
                )
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("icmf_send header extra data missing topic")
    }

    @Test
    fun incorrectMessageBody() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageBody = gtv("hej")
        val incorrectMessageBody = gtv("nej")

        val ops = createOpData(
                listOf(incorrectMessageBody),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1,
                messageExtraDataOverride = mapOf(
                        ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                                topic to TopicHeaderData(
                                        gtv(listOf(messageBody)).merkleHash(hashCalculator),
                                        -1L
                                ).toGtv()
                        ))
                )
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("invalid messages hash for topic")
    }

    @Test
    fun skippingMessage() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej0"), gtv("hej1")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        val ops1 = ops.subList(0, ops.size - 2)
        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops1)
        }.isInstanceOf<UserMistake>().messageContains("invalid messages hash for topic")
    }

    @Test
    fun skippingAllMessages() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej0"), gtv("hej1")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        val ops1 = ops.subList(0, ops.size - 4)
        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops1)
        }.isInstanceOf<UserMistake>().messageContains("Received header ops but no message ops")
    }

    @Test
    fun injectMessage() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        val injectedMessageBody = gtv("hej2")
        val injectedMessageHashOp = MessageHashOp(blockchainRID, topic, injectedMessageBody.merkleHash(hashCalculator)).toOpData()
        val injectedMessageOp = MessageOp(blockchainRID, topic, injectedMessageBody).toOpData()

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops + listOf(injectedMessageHashOp, injectedMessageOp))
        }.isInstanceOf<UserMistake>().messageContains("invalid messages hash for topic")
    }

    @Test
    fun invalidPreviousMessageHeight() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                previousMessageBlockHeight = 0,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                previousAnchorHeight = -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("icmf_send header extra has incorrect previous message height")
    }

    @Test
    fun invalidAnchorHeaderSignature() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val invalidSigner = cryptoSystem.generateKeyPair()
        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                messageSigner = IcmfTestClusterManagement.keyPair,
                anchorSigner = invalidSigner,
                -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("Unable to extract TopicHeaderData")
    }

    @Test
    fun incorrectBlockRidsInAnchorHeader() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1,
                anchorExtraDataOverride = mapOf(
                        ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                                topic to TopicHeaderData(gtv(listOf(gtv(ByteArray(32)))).merkleHash(hashCalculator), -1L).toGtv()
                        ))
                )
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("Invalid block-rid hash")
    }

    @Test
    fun invalidPreviousAnchorBlockHeight() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val ops = createOpData(
                listOf(gtv("hej")),
                previousMessageBlockHeight = -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                previousAnchorHeight = 0
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("icmf_anchor_headers header extra has incorrect previous message height")
    }

    @Test
    fun successWithSpilledMessages() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageOp = MessageOp(blockchainRID, topic, spilledMessage).toOpData()

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp)))
    }

    @Test
    fun incorrectSpilledMessageBody() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageOp = MessageOp(blockchainRID, topic, gtv("fel")).toOpData()

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp))
        }.isInstanceOf<UserMistake>().messageContains("Hash of message body")
    }

    @Test
    fun unexpectedSpilledMessage() {
        val unexpectedDbMock: IcmfReceiverDatabaseOperations = mock {
            on { loadLastMessageHeight(mockContext, blockchainRID, topic) } doReturn -1L
            on { loadLastAnchoredHeight(mockContext, cluster, topic) } doReturn -1L
            on { loadSpilledMessageCounts(mockContext, cluster, 0, topic) } doReturn mapOf()
        }
        val icmfReceiverSpecialTxExtension = createTxExt(unexpectedDbMock)

        val messageOp = MessageOp(blockchainRID, topic, spilledMessage).toOpData()

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp))
        }.isInstanceOf<UserMistake>().messageContains("Received unexpected message op for sende")
    }

    @Test
    fun successWithoutAnchoring() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 0)), null, null, null, null, null, specialTxSizeMargin, 10, maxMessageDelay))

        val messageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps))
    }

    @Test
    fun nonConfiguredOrigin() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(), null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val messageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps)
        }.isInstanceOf<UserMistake>().messageContains("is not allowed to send us messages on topic")
    }

    @Test
    fun nonConfiguredTopic() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverTopicsAndSpecificBlockchainConfig(listOf("another-topic"), null), null, null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("is not allowed to send us messages on topic")
    }

    @Test
    fun nonConfiguredSender() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverTopicsAndSpecificBlockchainConfig(null, listOf(IcmfReceiverSpecificBlockChainConfig(irrelevantBlockchainRID.data, topic, 0))), null, null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("is not allowed to send us messages on topic")
    }

    @Test
    fun `Topics in header that we dont receive messages ops for should not impact validation`() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val relevantMessageBodies = listOf(gtv("hej"))
        val irrelevantMessageBodies = listOf(gtv("hej on another topic"))
        val block = createBlockDetail(relevantMessageBodies, -1, IcmfTestClusterManagement.keyPair, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(relevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv(),
                        irrelevantTopic to TopicHeaderData(
                                gtv(irrelevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1).toGtv(),
                        irrelevantTopic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1).toGtv()
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()
        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(relevantMessageBodies)
        val ops = listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun `Topics in anchor header that we dont receive messages ops for should not impact validation`() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val relevantMessageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(relevantMessageBodies, -1, IcmfTestClusterManagement.keyPair, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(relevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1).toGtv(),
                        irrelevantTopic to TopicHeaderData(gtv(listOf(gtv(BlockchainRid.ZERO_RID.data))).merkleHash(hashCalculator), -1).toGtv()
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()
        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(relevantMessageBodies)
        val ops = listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun `Messages in anchor header from chains that we dont receive messages ops for should not impact validation`() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(
                IcmfReceiverTopicsAndSpecificBlockchainConfig(null, listOf(
                        IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 0))), null, null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val relevantMessageBody = gtv("hej")
        val block = createBlockDetail(listOf(relevantMessageBody), -1, IcmfTestClusterManagement.keyPair, senderBlockchainRid = blockchainRID, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(listOf(gtv(relevantMessageBody.merkleHash(hashCalculator)))).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )

        val irrelevantMessageBody = gtv("hej2")
        val block2 = createBlockDetail(listOf(irrelevantMessageBody), -1, IcmfTestClusterManagement.keyPair, senderBlockchainRid = irrelevantBlockchainRID, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(listOf(gtv(irrelevantMessageBody.merkleHash(hashCalculator)))).merkleHash(hashCalculator),
                                0
                        ).toGtv()
                )))
        )

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid), gtv(block2.rid))).merkleHash(hashCalculator), -1).toGtv(),
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()
        val relevantAnchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val irrelevantAnchoredHeaderOp = AnchoredHeaderOp(block2.header.data, block2.witness.data).toOpData()
        val relevantMessageHashOp = MessageHashOp(blockchainRID, topic, relevantMessageBody.merkleHash(hashCalculator)).toOpData()
        val irrelevantMessageHashOp = MessageHashOp(irrelevantBlockchainRID, topic, irrelevantMessageBody.merkleHash(hashCalculator)).toOpData()
        val relevantMessageOp = MessageOp(blockchainRID, topic, relevantMessageBody).toOpData()
        val ops = listOf(anchorHeaderOp, relevantAnchoredHeaderOp, relevantMessageHashOp, relevantMessageOp, irrelevantAnchoredHeaderOp, irrelevantMessageHashOp)

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun `Can handle multiple identical anchor header ops in same tx`() {
        val icmfReceiverSpecialTxExtension = createTxExt(MockIcmfReceiverDatabaseOperations())

        val relevantMessageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(relevantMessageBodies, -1, IcmfTestClusterManagement.keyPair, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(relevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )
        val secondTopicBlock = createBlockDetail(relevantMessageBodies, -1, IcmfTestClusterManagement.keyPair, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        secondTopic to TopicHeaderData(
                                gtv(relevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1).toGtv(),
                        secondTopic to TopicHeaderData(gtv(listOf(gtv(secondTopicBlock.rid))).merkleHash(hashCalculator), -1).toGtv()
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()
        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val secondTopicAnchoredHeaderOp = AnchoredHeaderOp(secondTopicBlock.header.data, secondTopicBlock.witness.data).toOpData()
        val messageOps = createMessageOps(relevantMessageBodies)
        val secondTopicMessageOps = createMessageOps(relevantMessageBodies, secondTopic)
        val ops = listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps + listOf(anchorHeaderOp, secondTopicAnchoredHeaderOp) + secondTopicMessageOps

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun `Topics in header that we dont receive messages ops for should not impact validation for local receivers`() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 0)), null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val relevantMessageBodies = listOf(gtv("hej"))
        val irrelevantMessageBodies = listOf(gtv("hej on another topic"))
        val block = createBlockDetail(relevantMessageBodies, -1, IcmfTestClusterManagement.keyPair, messageExtraDataOverride = mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(relevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv(),
                        irrelevantTopic to TopicHeaderData(
                                gtv(irrelevantMessageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                )))
        )

        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(relevantMessageBodies)
        val ops = listOf(nonAnchoredHeaderOp) + messageOps

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun `Messages before skipped height should be rejected`() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 1)), null, null, null, null, null, specialTxSizeMargin, messageLimit, maxMessageDelay))

        val messageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)
        val ops = listOf(nonAnchoredHeaderOp) + messageOps

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("is not allowed to send us messages on topic")
    }

    @Test
    fun `Messages can't exceed set message limit if signer`() {

        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 0)), null, null, null, null, null, specialTxSizeMargin, 1, maxMessageDelay))

        val messageBodies = listOf(gtv("hej1"), gtv("hej2"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps)
        }.isInstanceOf<UserMistake>().messageContains("Number of messages exceed limit")
    }

    @Test
    fun `Messages can exceed set message limit if not signer`() {

        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic, 0)), null, null, null, null, null, specialTxSizeMargin, 1, maxMessageDelay), nodeIsSigner = false)

        val messageBodies = listOf(gtv("hej1"), gtv("hej2"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps))
    }

    @Test
    fun `Non-relevant topics do not need to be anchored`() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val relevantMessages = listOf(gtv("hej"))
        val block = createBlockDetail(relevantMessages, -1, IcmfTestClusterManagement.keyPair, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                gtv(relevantMessages.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv(),
                        "another-topic" to TopicHeaderData(
                                gtv(listOf(gtv("hej2")).map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                -1
                        ).toGtv()
                ))
        ))

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1).toGtv()
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()

        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()

        val messageOps = createMessageOps(relevantMessages)

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps))
    }

    @Test
    fun `wrong sender in MessageHash op`() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageBody = gtv("hej")
        val block = createBlockDetail(listOf(messageBody), -1, IcmfTestClusterManagement.keyPair, senderBlockchainRid = blockchainRID)
        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), -1
                        ).toGtv()
                ))
        ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.keyPair).signDigest(anchorBlockRid))
        ).getRawData()
        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()
        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageHashOp = MessageHashOp(irrelevantBlockchainRID, topic, messageBody.merkleHash(hashCalculator)).toOpData()
        val messageOp = MessageOp(irrelevantBlockchainRID, topic, messageBody).toOpData()
        val ops = listOf(anchorHeaderOp, anchoredHeaderOp, messageHashOp, messageOp)

        assertFailure {
            icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops)
        }.isInstanceOf<UserMistake>().messageContains("in __anchored_icmf_message_hash does not match header sender")
    }

    @Test
    fun `trigger block building`() {
        val clock: Clock = mock()
        val icmfReceiverSpecialTxExtension = createTxExt(clock = clock)
        val pipe: IcmfPipe<TopicRoute, Long, IcmfAnchorPacket, String> = mock {
            on { route } doReturn TopicRoute("topic", listOf())
            on { haveNewPacketsForSure() } doReturn false
        }
        val receiver: IcmfReceiver<TopicRoute, Long, IcmfAnchorPacket, String> = mock {
            on { getRelevantPipes() } doReturn listOf(pipe)
        }
        icmfReceiverSpecialTxExtension.anchoredReceivers.add(receiver)

        whenever(clock.millis()) doReturn 10000
        icmfReceiverSpecialTxExtension.blockCommitted(mock())
        assertFalse(icmfReceiverSpecialTxExtension.shouldBuildBlock())
        verify(pipe, never()).haveNewPacketsForSure()
        whenever(pipe.haveNewPacketsForSure()) doReturn true
        whenever(clock.millis()) doReturn 10100
        assertFalse(icmfReceiverSpecialTxExtension.shouldBuildBlock())
        verify(pipe, never()).haveNewPacketsForSure()
        whenever(clock.millis()) doReturn 11001
        assertTrue(icmfReceiverSpecialTxExtension.shouldBuildBlock())
        verify(pipe).haveNewPacketsForSure()
        whenever(clock.millis()) doReturn 11021
        assertTrue(icmfReceiverSpecialTxExtension.shouldBuildBlock())

        whenever(clock.millis()) doReturn 12000
        icmfReceiverSpecialTxExtension.blockCommitted(mock())
        assertFalse(icmfReceiverSpecialTxExtension.shouldBuildBlock())
    }

    private fun createTxExt(
            databaseOperations: IcmfReceiverDatabaseOperations = dbMock,
            icmfConfig: IcmfReceiverBlockchainConfigData = defaultIcmfConfig,
            nodeIsSigner: Boolean = true,
            clock: Clock = Clock.systemUTC(),
    ): IcmfReceiverSpecialTxExtension = IcmfReceiverSpecialTxExtension(databaseOperations, clock).apply {
        init(mockModule, chainID, blockchainRID, cryptoSystem)
        blockchainConfigProvider = BlockchainConfigProvider { _ -> listOf(IcmfTestClusterManagement.keyPair.pubKey) }
        icmfReceiverBlockchainConfigData = icmfConfig
        isSigner = { nodeIsSigner }
    }

    private fun createOpData(
            messageBodies: List<Gtv>,
            previousMessageBlockHeight: Long,
            messageSigner: KeyPair,
            anchorSigner: KeyPair,
            previousAnchorHeight: Long,
            messageExtraDataOverride: Map<String, Gtv>? = null,
            anchorExtraDataOverride: Map<String, Gtv>? = null
    ): List<OpData> {
        val block = createBlockDetail(messageBodies, previousMessageBlockHeight, messageSigner, messageExtraDataOverride)

        val anchorHeader = makeBlockHeader(anchorBlockchainRID, BlockRid(anchorBlockchainRID.data), 0, anchorExtraDataOverride
                ?: mapOf(
                        ICMF_ANCHOR_HEADERS_EXTRA to gtv(mapOf(
                                topic to TopicHeaderData(gtv(listOf(gtv(block.rid))).merkleHash(hashCalculator), previousAnchorHeight).toGtv()
                        ))
                ))
        val anchorBlockRid = anchorHeader.toGtv().merkleHash(hashCalculator)
        val rawAnchorWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(anchorSigner).signDigest(anchorBlockRid))
        ).getRawData()

        val anchorHeaderOp = AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()

        val anchoredHeaderOp = AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()

        val messageOps = createMessageOps(messageBodies)

        return listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps
    }

    private fun createBlockDetail(messageBodies: List<Gtv>, previousMessageBlockHeight: Long, messageSigner: KeyPair, messageExtraDataOverride: Map<String, Gtv>? = null, senderBlockchainRid: BlockchainRid = blockchainRID): BlockDetail {
        val header = makeBlockHeader(senderBlockchainRid, BlockRid(senderBlockchainRid.data), 0, messageExtraDataOverride
                ?: mapOf(
                        ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                                topic to TopicHeaderData(
                                        gtv(messageBodies.map { gtv(it.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                                        previousMessageBlockHeight
                                ).toGtv()
                        ))
                ))

        val gtvBlockHeader = header.toGtv()
        val blockRid = gtvBlockHeader.merkleHash(hashCalculator)
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(messageSigner).signDigest(blockRid))
        ).getRawData()
        return BlockDetail(
                blockRid.wrap(),
                header.getPreviousBlockRid().wrap(),
                GtvEncoder.encodeGtv(gtvBlockHeader).wrap(),
                header.getHeight(),
                listOf(),
                rawWitness.wrap(),
                header.getTimestamp()
        )
    }

    private fun createMessageOps(messageBodies: List<Gtv>, messageTopic: String = topic) = messageBodies.flatMap {
        listOf(
                MessageHashOp(blockchainRID, messageTopic, it.merkleHash(hashCalculator)).toOpData(),
                MessageOp(blockchainRID, messageTopic, it).toOpData()
        )
    }

    private fun makeBlockHeader(blockchainRID: BlockchainRid, previousBlockRid: BlockRid, height: Long, extra: Map<String, Gtv>) = BlockHeaderData(
            gtvBlockchainRid = gtv(blockchainRID),
            gtvPreviousBlockRid = gtv(previousBlockRid.data),
            gtvMerkleRootHash = gtv(ByteArray(32)),
            gtvTimestamp = gtv(height),
            gtvHeight = gtv(height),
            gtvDependencies = GtvNull,
            gtvExtra = gtv(extra)
    )
}

// Used for tests where we need to verify previous heights that are written to DB
class MockIcmfReceiverDatabaseOperations : IcmfReceiverDatabaseOperations {
    private val lastAnchoredHeights = mutableMapOf<Pair<String, String>, Long>()

    override fun initialize(ctx: EContext) {}

    override fun loadLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String): Long =
            lastAnchoredHeights.getOrDefault(clusterName to topic, -1)

    override fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight> {
        throw NotImplementedError("not used in mock")
    }

    override fun saveLastAnchoredHeight(ctx: EContext, clusterName: String, topic: String, anchorHeight: Long) {
        lastAnchoredHeights[clusterName to topic] = anchorHeight
    }

    override fun loadAllLastMessageHeights(ctx: EContext): List<MessageHeightForSender> {
        throw NotImplementedError("not used in mock")
    }

    override fun loadLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String): Long = -1

    override fun saveLastMessageHeight(ctx: EContext, sender: BlockchainRid, topic: String, height: Long) {}

    override fun loadOldestSpilledMessage(ctx: EContext, sender: BlockchainRid, topic: String): SpilledMessage {
        throw NotImplementedError("not used in mock")
    }

    override fun loadSpilledMessageCounts(ctx: EContext, cluster: String, anchorHeight: Long, topic: String): Map<BlockchainRid, Int> {
        return mapOf()
    }

    override fun saveSpilledMessage(ctx: EContext, cluster: String, anchorHeight: Long, sender: BlockchainRid, topic: String, hash: ByteArray, merkleHashVersion: Long) {
        throw NotImplementedError("not used in mock")
    }

    override fun imprecateSpilledMessage(ctx: EContext, serial: Long) {
        throw NotImplementedError("not used in mock")
    }

    override fun deleteDappProvidedReceiverTopics(ctx: EContext) {
        throw NotImplementedError("not used in mock")
    }

    override fun saveDappProvidedReceiverTopics(ctx: EContext, topics: List<IcmfReceiverEventTopic>) {
        throw NotImplementedError("not used in mock")
    }

    override fun deleteDappProvidedReceiverTopics(ctx: EContext, topics: List<IcmfReceiverEventTopic>) {
        throw NotImplementedError("not used in mock")
    }

    override fun loadDappProvidedReceiverTopics(ctx: EContext): List<IcmfReceiverEventTopic> {
        throw NotImplementedError("not used in mock")
    }
}
