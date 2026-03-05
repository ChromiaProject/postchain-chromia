package net.postchain.d1.anchoring

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.extension.MERKLE_HASH_VERSION_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BATCH_BLOCK_HEADER
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BLOCK_HEADER
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class AnchoringValidationTest {
    private val mockModule: GTXModule = mock {
        on { query(any(), eq("get_last_anchored_block"), any()) }.doReturn(GtvNull)
    }
    private val mockContext: BlockEContext = mock {}

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val chainID: Long = 1
    private val blockchainRID = BlockchainRid.buildRepeat(1)
    private val signer = KeyPairHelper.keyPair(0)
    private val blockchainConfigProvider: BlockchainConfigProvider = mock {
        on { getRelevantPeers(any()) } doReturn listOf(signer.pubKey)
    }

    private val batchModeConfig = AnchoringBlockchainConfigData.fromGtv(gtv(mapOf("batch_mode" to gtv(true))))

    @Test
    fun success() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader0 = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid0 = blockHeader0.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness0 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid0))
        ).getRawData()

        val blockHeader1 = makeBlockHeader(blockchainRID, BlockRid(blockRid0), 1)
        val blockRid1 = blockHeader1.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness1 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid1))
        ).getRawData()

        assertTrue(txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                listOf(
                        OpData(OP_BLOCK_HEADER, arrayOf(
                                gtv(blockRid0),
                                blockHeader0,
                                gtv(rawWitness0))),
                        OpData(OP_BLOCK_HEADER, arrayOf(
                                gtv(blockRid1),
                                blockHeader1,
                                gtv(rawWitness1)))
                )))
    }

    @Test
    fun invalidParameters() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockRid = BlockRid.buildRepeat(2)
        val rawWitness = ByteArray(0)

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(OpData(OP_BLOCK_HEADER, arrayOf(
                            gtv(blockRid.data),
                            GtvNull,
                            gtv(rawWitness)))))
        }.isInstanceOf<UserMistake>().messageContains("Invalid operation")
    }

    @Test
    fun duplicateHeader() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness))),
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Adding the same header twice")
    }

    @Test
    fun negativeHeight() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), -1)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Invalid operation")
    }

    @Test
    fun unchainedHeaders() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader0 = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid0 = blockHeader0.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness0 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid0))
        ).getRawData()

        val blockHeader1 = makeBlockHeader(blockchainRID, BlockRid.buildRepeat(17), 1)
        val blockRid1 = blockHeader1.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness1 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid1))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid0),
                                    blockHeader0,
                                    gtv(rawWitness0))),
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid1),
                                    blockHeader1,
                                    gtv(rawWitness1)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("header.prevBlockRID != expected previous BlockRID")
    }

    @Test
    fun nonConsecutiveHeaders() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader0 = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid0 = blockHeader0.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness0 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid0))
        ).getRawData()

        val blockHeader1 = makeBlockHeader(blockchainRID, BlockRid(blockRid0), 2)
        val blockRid1 = blockHeader1.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness1 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid1))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid0),
                                    blockHeader0,
                                    gtv(rawWitness0))),
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid1),
                                    blockHeader1,
                                    gtv(rawWitness1)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Expected height: 1, got: 2")
    }

    @Test
    fun invalidBlockRid() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid = BlockRid.buildRepeat(17).data
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Invalid block-rid:")
    }

    @Test
    fun noRelevantPeersFound() {
        val bcConfigProvider: BlockchainConfigProvider = mock {
            on { getRelevantPeers(any()) } doThrow (UserMistake("Can't find peers ..."))
        }

        val txExtension = createAnchorSpecialTxExtension(bcConfigProvider = bcConfigProvider)

        val blockHeader = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("no peers")
    }

    @Test
    fun invalidSignature() {
        val txExtension = createAnchorSpecialTxExtension()

        val blockHeader = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val invalidSigner = KeyPairHelper.keyPair(1)
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(invalidSigner).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Invalid signatures")
    }

    @Test
    fun irrelevantChain() {
        val txExtension = createAnchorSpecialTxExtension()

        val irrelevantChain = BlockchainRid.buildRepeat(2)
        val blockHeader = makeBlockHeader(irrelevantChain, BlockRid(irrelevantChain.data), 0)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BLOCK_HEADER, arrayOf(
                                    gtv(blockRid),
                                    blockHeader,
                                    gtv(rawWitness)))
                    ))
        }.isInstanceOf<UserMistake>().messageContains("are not allowed to be anchored in this chain")
    }

    @Test
    fun irrelevantChainIsOkForReplicas() {
        val txExtension = createAnchorSpecialTxExtension(isSigner = false)

        val irrelevantChain = BlockchainRid.buildRepeat(2)
        val blockHeader = makeBlockHeader(irrelevantChain, BlockRid(irrelevantChain.data), 0)
        val blockRid = blockHeader.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid))
        ).getRawData()

        assertTrue(txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                listOf(
                        OpData(OP_BLOCK_HEADER, arrayOf(
                                gtv(blockRid),
                                blockHeader,
                                gtv(rawWitness)))
                )))
    }

    @Test
    fun operationSize() {
        val packet = AnchoringPacket(
                height = 0,
                blockRid = ByteArray(32) { 17 },
                rawHeader = GtvEncoder.encodeGtv(gtv(gtv("foobar"), gtv(4711))),
                rawWitness = ByteArray(16) { 123 }
        )
        MultiOpAnchoringSpecialTxBuilder().apply {
            val size = calculateRequiredSize(packet)
            addAnchorPacket(packet)
            val ops = build()
            assertEquals(GtvEncoder.encodeGtv(GtxOp.fromOpData(ops[0]).toGtv()).size, size)
        }
    }

    @Test
    fun successBatch() {
        val txExtension = createAnchorSpecialTxExtension(config = batchModeConfig)

        val blockHeader0 = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid0 = blockHeader0.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness0 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid0))
        ).getRawData()

        val blockHeader1 = makeBlockHeader(blockchainRID, BlockRid(blockRid0), 1)
        val blockRid1 = blockHeader1.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness1 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid1))
        ).getRawData()

        assertTrue(txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                listOf(
                        OpData(OP_BATCH_BLOCK_HEADER, arrayOf(gtv(
                                gtv(listOf(
                                        gtv(blockRid0),
                                        blockHeader0,
                                        gtv(rawWitness0)
                                )),
                                gtv(listOf(
                                        gtv(blockRid1),
                                        blockHeader1,
                                        gtv(rawWitness1)
                                ))
                        )))
                ))
        )
    }

    @Test
    fun onlyOneOpInBatchModeAllowed() {
        val txExtension = createAnchorSpecialTxExtension(config = batchModeConfig)

        val blockHeader0 = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0)
        val blockRid0 = blockHeader0.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness0 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid0))
        ).getRawData()

        val blockHeader1 = makeBlockHeader(blockchainRID, BlockRid(blockRid0), 1)
        val blockRid1 = blockHeader1.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem))
        val rawWitness1 = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(signer).signDigest(blockRid1))
        ).getRawData()

        assertFailure {
            txExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext,
                    listOf(
                            OpData(OP_BATCH_BLOCK_HEADER, arrayOf(gtv(
                                    gtv(listOf(
                                            gtv(blockRid0),
                                            blockHeader0,
                                            gtv(rawWitness0)
                                    )),
                            ))),
                            OpData(OP_BATCH_BLOCK_HEADER, arrayOf(gtv(
                                    gtv(listOf(
                                            gtv(blockRid1),
                                            blockHeader1,
                                            gtv(rawWitness1)
                                    ))
                            ))),
                    ))
        }.isInstanceOf<UserMistake>().messageContains("Only one operation allowed when batching")
    }

    private fun createAnchorSpecialTxExtension(
            isSigner: Boolean = true,
            bcConfigProvider: BlockchainConfigProvider? = null,
            config: AnchoringBlockchainConfigData = AnchoringBlockchainConfigData.fromGtv(gtv(mapOf()))
    ): AnchoringSpecialTxExtension {
        val txExtension = AnchoringSpecialTxExtension { _, _, _ -> mock() }
        txExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        txExtension.blockchainConfigProvider = bcConfigProvider ?: blockchainConfigProvider
        txExtension.anchoringPipeManager = AnchoringPipeManager(mock(), mock {
            on { getRelevantChains(any()) } doReturn setOf(blockchainRID)
        })
        txExtension.isSigner = { isSigner }
        txExtension.anchoringConfig = config
        return txExtension
    }

    private fun makeBlockHeader(blockchainRID: BlockchainRid, previousBlockRid: BlockRid, height: Long) = BlockHeaderData(
            gtvBlockchainRid = gtv(blockchainRID),
            gtvPreviousBlockRid = gtv(previousBlockRid.data),
            gtvMerkleRootHash = gtv(ByteArray(32)),
            gtvTimestamp = gtv(height),
            gtvHeight = gtv(height),
            gtvDependencies = GtvNull,
            gtvExtra = gtv(mapOf(
                    MERKLE_HASH_VERSION_EXTRA_HEADER to gtv(2)
            ))
    ).toGtv()
}
