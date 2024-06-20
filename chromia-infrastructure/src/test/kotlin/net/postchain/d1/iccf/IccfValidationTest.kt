package net.postchain.d1.iccf

import net.postchain.base.BaseBlockWitness
import net.postchain.base.ConfirmationProof
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BLOCK_HEADER
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder.Companion.ICCF_OP_NAME
import net.postchain.d1.query.ChromiaQueryProvider
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.generateProof
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXOpMistake
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class IccfValidationTest {
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val sourceBlockchainRid = BlockchainRid.buildRepeat(0)
    private val targetBlockchainRid = BlockchainRid.buildRepeat(1)
    private val clusterAnchoringChainRid = BlockchainRid.buildRepeat(2)
    private val sourceBlockchainSigners = listOf(
            cryptoSystem.generateKeyPair(),
            cryptoSystem.generateKeyPair()
    )
    private val clusterAnchoringChainSigners = listOf(
            cryptoSystem.generateKeyPair(),
            cryptoSystem.generateKeyPair()
    )

    private val otherTxHash = ByteArray(32) { 0 }
    private val txToProveHash = ByteArray(32) { 1 }
    private val txProof = gtv(gtv(otherTxHash), gtv(txToProveHash)).generateProof(listOf(1), hashCalculator)
    private val sourceBlockHeader = BlockHeaderData(
            gtv(sourceBlockchainRid),
            gtv(sourceBlockchainRid),
            gtv(txProof.merkleHash(hashCalculator)),
            gtv(0),
            gtv(0),
            GtvNull,
            gtv(mapOf())
    ).toGtv()
    private val sourceBlockRid = sourceBlockHeader.merkleHash(hashCalculator)
    private val sourceBlockWitness = BaseBlockWitness.fromSignatures(sourceBlockchainSigners.map { cryptoSystem.buildSigMaker(it).signDigest(sourceBlockRid) }.toTypedArray())

    private val confirmationProof = GtvObjectMapper.toGtvDictionary(ConfirmationProof(
            txToProveHash,
            GtvEncoder.encodeGtv(sourceBlockHeader),
            sourceBlockWitness,
            txProof,
            1L
    ))

    private val clusterAnchoringTx = Gtx(
            GtxBody(clusterAnchoringChainRid, listOf(GtxOp(OP_BLOCK_HEADER, gtv(sourceBlockRid), sourceBlockHeader, gtv(sourceBlockWitness.getRawData()))), listOf()),
            listOf()
    )
    private val clusterAnchoringTxHash = clusterAnchoringTx.toGtv().merkleHash(hashCalculator)
    private val clusterAnchoringTxProof = gtv(gtv(clusterAnchoringTxHash)).generateProof(listOf(0), hashCalculator)
    private val clusterAnchoringBlockHeader = BlockHeaderData(
            gtv(clusterAnchoringChainRid),
            gtv(clusterAnchoringChainRid),
            gtv(clusterAnchoringTxProof.merkleHash(hashCalculator)),
            gtv(0),
            gtv(0),
            GtvNull,
            gtv(mapOf())
    ).toGtv()
    private val clusterAnchoringBlockRid = clusterAnchoringBlockHeader.merkleHash(hashCalculator)
    private val clusterAnchoringBlockWitness = BaseBlockWitness.fromSignatures(clusterAnchoringChainSigners.map { cryptoSystem.buildSigMaker(it).signDigest(clusterAnchoringBlockRid) }.toTypedArray())

    private val clusterAnchoringConfirmationProof = GtvObjectMapper.toGtvDictionary(ConfirmationProof(
            clusterAnchoringTxHash,
            GtvEncoder.encodeGtv(clusterAnchoringBlockHeader),
            clusterAnchoringBlockWitness,
            clusterAnchoringTxProof,
            0L
    ))

    private val dummyOp = OpData("dummy", arrayOf())

    @Test
    fun intraClusterIccf() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
        }
        val clusterAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(sourceBlockchainRid), "block_rid" to gtv(sourceBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getClusterAnchoringQuery() } doReturn clusterAnchoringClient
        }

        val iccfExtOpData = buildExtOpData(confirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertDoesNotThrow {
            iccfGTXOperation.checkCorrectness()
        }
        verify(chromiaQueryProvider).getClusterAnchoringQuery()
    }

    @Test
    fun intraClusterIccfForReplica() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {}

        val iccfExtOpData = buildExtOpData(confirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = true
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertDoesNotThrow {
            iccfGTXOperation.checkCorrectness()
        }
        verify(chromiaQueryProvider, never()).getClusterAnchoringQuery()
    }

    @Test
    fun intraClusterIccfWhenTargetIsInterCluster() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(sourceBlockchainRid) } doReturn "clusterA"
            on { getClusterOfBlockchain(targetBlockchainRid) } doReturn "clusterB"
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {}

        val iccfExtOpData = buildExtOpData(confirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun invalidSourceBlockSignatures() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn listOf(cryptoSystem.generateKeyPair().pubKey, cryptoSystem.generateKeyPair().pubKey)
        }
        val clusterAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(sourceBlockchainRid), "block_rid" to gtv(sourceBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getClusterAnchoringQuery() } doReturn clusterAnchoringClient
        }

        val iccfExtOpData = buildExtOpData(confirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun invalidProofTreeRootHash() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
        }
        val clusterAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(sourceBlockchainRid), "block_rid" to gtv(sourceBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getClusterAnchoringQuery() } doReturn clusterAnchoringClient
        }

        val swappedPositionProof = gtv(gtv(txToProveHash), gtv(otherTxHash)).generateProof(listOf(0), hashCalculator)
        val invalidConfirmationProof = GtvObjectMapper.toGtvDictionary(ConfirmationProof(
                txToProveHash,
                GtvEncoder.encodeGtv(sourceBlockHeader),
                sourceBlockWitness,
                swappedPositionProof,
                0L
        ))

        val iccfExtOpData = buildExtOpData(invalidConfirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    private fun buildExtOpData(confirmationProof: GtvDictionary): ExtOpData {
        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        return iccfExtOpData
    }

    @Test
    fun invalidProofTreeTxHash() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
        }
        val clusterAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(sourceBlockchainRid), "block_rid" to gtv(sourceBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getClusterAnchoringQuery() } doReturn clusterAnchoringClient
        }

        val swappedPositionProof = gtv(gtv(otherTxHash), gtv(txToProveHash)).generateProof(listOf(0), hashCalculator)
        val invalidConfirmationProof = GtvObjectMapper.toGtvDictionary(ConfirmationProof(
                txToProveHash,
                GtvEncoder.encodeGtv(sourceBlockHeader),
                sourceBlockWitness,
                swappedPositionProof,
                0L
        ))

        val iccfExtOpData = buildExtOpData(invalidConfirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun sourceBlockNotAnchored() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(any()) } doReturn "clusterA"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
        }
        val clusterAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(sourceBlockchainRid), "block_rid" to gtv(sourceBlockRid))
                ))
            } doReturn gtv(false)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getClusterAnchoringQuery() } doReturn clusterAnchoringClient
        }

        val iccfExtOpData = buildExtOpData(confirmationProof)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun intraNetworkIccf() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(sourceBlockchainRid) } doReturn "clusterA"
            on { getClusterOfBlockchain(targetBlockchainRid) } doReturn "clusterB"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
            on { getBlockchainPeers(clusterAnchoringChainRid, 0) } doReturn clusterAnchoringChainSigners.map { it.pubKey }
            on { getClusterInfo("clusterA") } doReturn D1ClusterInfo("clusterA", clusterAnchoringChainRid, listOf())
        }
        val systemAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(clusterAnchoringChainRid), "block_rid" to gtv(clusterAnchoringBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getSystemAnchoringQuery() } doReturn systemAnchoringClient
        }

        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)), gtv(clusterAnchoringTx.encode()), gtv(0), gtv(GtvEncoder.encodeGtv(clusterAnchoringConfirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertDoesNotThrow {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun intraNetworkSourceBlockAnchoringOperationMissing() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(sourceBlockchainRid) } doReturn "clusterA"
            on { getClusterOfBlockchain(targetBlockchainRid) } doReturn "clusterB"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
            on { getBlockchainPeers(clusterAnchoringChainRid, 0) } doReturn clusterAnchoringChainSigners.map { it.pubKey }
            on { getClusterInfo("clusterA") } doReturn D1ClusterInfo("clusterA", clusterAnchoringChainRid, listOf())
        }
        val systemAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(clusterAnchoringChainRid), "block_rid" to gtv(clusterAnchoringBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getSystemAnchoringQuery() } doReturn systemAnchoringClient
        }

        val emtpyClusterAnchoringTx = Gtx(
                GtxBody(clusterAnchoringChainRid, listOf(), listOf()),
                listOf()
        )

        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)), gtv(emtpyClusterAnchoringTx.encode()), gtv(0), gtv(GtvEncoder.encodeGtv(clusterAnchoringConfirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun intraNetworkClusterAnchoringTxFromIncorrectChain() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(sourceBlockchainRid) } doReturn "clusterA"
            on { getClusterOfBlockchain(targetBlockchainRid) } doReturn "clusterB"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
            on { getBlockchainPeers(clusterAnchoringChainRid, 0) } doReturn clusterAnchoringChainSigners.map { it.pubKey }
            on { getClusterInfo("clusterA") } doReturn D1ClusterInfo("clusterA", clusterAnchoringChainRid, listOf())
        }
        val systemAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(clusterAnchoringChainRid), "block_rid" to gtv(clusterAnchoringBlockRid))
                ))
            } doReturn gtv(true)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getSystemAnchoringQuery() } doReturn systemAnchoringClient
        }

        val clusterAnchoringTxFromWrongChain = Gtx(
                GtxBody(BlockchainRid.buildRepeat(4), listOf(GtxOp(OP_BLOCK_HEADER, gtv(sourceBlockRid), sourceBlockHeader, gtv(sourceBlockWitness.getRawData()))), listOf()),
                listOf()
        )

        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)), gtv(clusterAnchoringTxFromWrongChain.encode()), gtv(0), gtv(GtvEncoder.encodeGtv(clusterAnchoringConfirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun clusterAnchoringBlockNotAnchoredInSystemAnchoringChain() {
        val clusterManagement: ClusterManagement = mock {
            on { getClusterOfBlockchain(sourceBlockchainRid) } doReturn "clusterA"
            on { getClusterOfBlockchain(targetBlockchainRid) } doReturn "clusterB"
            on { getBlockchainPeers(sourceBlockchainRid, 0) } doReturn sourceBlockchainSigners.map { it.pubKey }
            on { getBlockchainPeers(clusterAnchoringChainRid, 0) } doReturn clusterAnchoringChainSigners.map { it.pubKey }
            on { getClusterInfo("clusterA") } doReturn D1ClusterInfo("clusterA", clusterAnchoringChainRid, listOf())
        }
        val systemAnchoringClient: PostchainBlockClient = mock {
            on {
                query("is_block_anchored", gtv(mapOf(
                        "blockchain_rid" to gtv(clusterAnchoringChainRid), "block_rid" to gtv(clusterAnchoringBlockRid))
                ))
            } doReturn gtv(false)
        }
        val chromiaQueryProvider: ChromiaQueryProvider = mock {
            on { getSystemAnchoringQuery() } doReturn systemAnchoringClient
        }

        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)), gtv(clusterAnchoringTx.encode()), gtv(0), gtv(GtvEncoder.encodeGtv(clusterAnchoringConfirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = clusterManagement
            this.queryProvider = chromiaQueryProvider
            this.nodeIsReplica = false
        }
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<UserMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun wrongNumberOfArgs() {
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = mock {}
            this.queryProvider = mock {}
            this.nodeIsReplica = false
        }
        val iccfGtxOpMissingProofArg = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOpMissingProofArg), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOpMissingProofArg.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<GTXOpMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun wrongArgumentType() {
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = mock {}
            this.queryProvider = mock {}
            this.nodeIsReplica = false
        }
        val iccfGtxOpMissingProofArg = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(1))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOpMissingProofArg), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOpMissingProofArg.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray() + dummyOp)
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<GTXOpMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }

    @Test
    fun noOtherOperationsInTx() {
        val iccfContext = IccfGTXModuleContext().apply {
            this.cryptoSystem = this@IccfValidationTest.cryptoSystem
            this.clusterManagement = mock {}
            this.queryProvider = mock {}
            this.nodeIsReplica = false
        }

        val iccfGtxOp = GtxOp(ICCF_OP_NAME, gtv(sourceBlockchainRid), gtv(txToProveHash), gtv(GtvEncoder.encodeGtv(confirmationProof)))
        val gtxBody = GtxBody(targetBlockchainRid, listOf(iccfGtxOp), listOf())
        val iccfExtOpData = ExtOpData.build(iccfGtxOp.asOpData(), 0, gtxBody, gtxBody.operations.map { it.asOpData() }.toTypedArray())
        val iccfGTXOperation = IccfGTXOperation(iccfContext, iccfExtOpData)

        assertThrows<GTXOpMistake> {
            iccfGTXOperation.checkCorrectness()
        }
    }
}
