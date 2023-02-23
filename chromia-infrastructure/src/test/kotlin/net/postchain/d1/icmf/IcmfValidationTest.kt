package net.postchain.d1.icmf

import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.core.BlockDetail
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.TopicHeaderData
import net.postchain.d1.anchoring.cluster.ICMF_ANCHOR_HEADERS_EXTRA
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class IcmfValidationTest {
    private val cluster = IcmfTestClusterManagement.senderCluster
    private val topic = "my-topic"
    private val anchorBlockchainRID = BlockchainRid.buildRepeat(0)
    private val blockchainRID = BlockchainRid.buildRepeat(1)
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val chainID: Long = 1
    private val spilledMessage = gtv("hej")
    private val defaultIcmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverGlobalConfig(listOf(topic), null), null)

    private val mockModule: GTXModule = mock {}
    private val mockContext: BlockEContext = mock {}
    private val dbMock: IcmfDatabaseOperations = mock {
        on { loadLastMessageHeight(mockContext, blockchainRID, topic) } doReturn -1L
        on { loadLastAnchoredHeight(mockContext, cluster, topic) } doReturn -1L
        on { loadOldestSpilledMessage(mockContext, blockchainRID, topic) } doReturn SpilledMessage(
                0,
                spilledMessage.merkleHash(hashCalculator),
                cluster,
                0
        )
        on { loadSpilledMessageCounts(mockContext, cluster, 0, topic) } doReturn mapOf()
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

        val anchoredHeaderOp = OpData(IcmfReceiverSpecialTxExtension.AnchoredHeaderOp.OP_NAME, arrayOf(
                GtvNull,
                GtvNull
        ))

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(anchoredHeaderOp)))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(ops[0], ops[2], ops[1])))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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
                                        gtv(listOf(messageBody)).merkleHash(GtvMerkleHashCalculator(cryptoSystem)),
                                        -1L
                                ).toGtv()
                        ))
                )
        )

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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
        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops1))
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
        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops1))
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
        val injectedMessageHashOp = IcmfReceiverSpecialTxExtension.MessageHashOp(blockchainRID, topic, injectedMessageBody.merkleHash(hashCalculator)).toOpData()
        val injectedMessageOp = IcmfReceiverSpecialTxExtension.MessageOp(blockchainRID, topic, injectedMessageBody).toOpData()

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops + listOf(injectedMessageHashOp, injectedMessageOp)))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
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

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun successWithSpilledMessages() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageOp = IcmfReceiverSpecialTxExtension.MessageOp(blockchainRID, topic, spilledMessage).toOpData()

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp)))
    }

    @Test
    fun incorrectSpilledMessageBody() {
        val icmfReceiverSpecialTxExtension = createTxExt()

        val messageOp = IcmfReceiverSpecialTxExtension.MessageOp(blockchainRID, topic, gtv("fel")).toOpData()

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp)))
    }

    @Test
    fun unexpectedSpilledMessage() {
        val unexpectedDbMock: IcmfDatabaseOperations = mock {
            on { loadLastMessageHeight(mockContext, blockchainRID, topic) } doReturn -1L
            on { loadLastAnchoredHeight(mockContext, cluster, topic) } doReturn -1L
            on { loadSpilledMessageCounts(mockContext, cluster, 0, topic) } doReturn mapOf()
        }
        val icmfReceiverSpecialTxExtension = createTxExt(unexpectedDbMock)

        val messageOp = IcmfReceiverSpecialTxExtension.MessageOp(blockchainRID, topic, spilledMessage).toOpData()

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp)))
    }

    @Test
    fun successWithoutAnchoring() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf(IcmfReceiverSpecificBlockChainConfig(blockchainRID.data, topic))))

        val messageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertTrue(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps))
    }

    @Test
    fun nonConfiguredOrigin() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(null, listOf()))

        val messageBodies = listOf(gtv("hej"))
        val block = createBlockDetail(
                messageBodies,
                -1,
                IcmfTestClusterManagement.keyPair
        )
        val nonAnchoredHeaderOp = IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp(block.header.data, block.witness.data).toOpData()
        val messageOps = createMessageOps(messageBodies)

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(nonAnchoredHeaderOp) + messageOps))
    }

    @Test
    fun nonConfiguredTopic() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverGlobalConfig(listOf("another-topic"), null), null))

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    @Test
    fun nonConfiguredSender() {
        val icmfReceiverSpecialTxExtension = createTxExt(icmfConfig = IcmfReceiverBlockchainConfigData(IcmfReceiverGlobalConfig(null, listOf(IcmfReceiverSpecificBlockChainConfig(BlockchainRid.buildRepeat(2).data, topic))), null))

        val ops = createOpData(
                listOf(gtv("hej")),
                -1,
                IcmfTestClusterManagement.keyPair,
                IcmfTestClusterManagement.keyPair,
                -1
        )

        assertFalse(icmfReceiverSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, ops))
    }

    private fun createTxExt(databaseOperations: IcmfDatabaseOperations = dbMock, icmfConfig: IcmfReceiverBlockchainConfigData = defaultIcmfConfig): IcmfReceiverSpecialTxExtension = IcmfReceiverSpecialTxExtension(databaseOperations).apply {
        init(mockModule, chainID, blockchainRID, cryptoSystem)
        clusterManagement = IcmfTestClusterManagement()
        icmfReceiverBlockchainConfigData = icmfConfig
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

        val anchorHeaderOp = IcmfReceiverSpecialTxExtension.AnchorHeaderOp(cluster, GtvEncoder.encodeGtv(anchorHeader.toGtv()), rawAnchorWitness).toOpData()

        val anchoredHeaderOp = IcmfReceiverSpecialTxExtension.AnchoredHeaderOp(block.header.data, block.witness.data).toOpData()

        val messageOps = createMessageOps(messageBodies)

        return listOf(anchorHeaderOp, anchoredHeaderOp) + messageOps
    }

    private fun createBlockDetail(messageBodies: List<Gtv>, previousMessageBlockHeight: Long, messageSigner: KeyPair, messageExtraDataOverride: Map<String, Gtv>? = null): BlockDetail {
        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, messageExtraDataOverride ?: mapOf(
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

    private fun createMessageOps(messageBodies: List<Gtv>) = messageBodies.flatMap {
        listOf(
                IcmfReceiverSpecialTxExtension.MessageHashOp(blockchainRID, topic, it.merkleHash(hashCalculator)).toOpData(),
                IcmfReceiverSpecialTxExtension.MessageOp(blockchainRID, topic, it).toOpData()
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
