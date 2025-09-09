package net.postchain.d1.iccf

import net.postchain.base.ConfirmationProof
import net.postchain.base.extension.getMerkleHashVersion
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.chromia.model.BlockchainState
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.TxEContext
import net.postchain.d1.Validation
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.d1.rell.anchoring_chain_common.isBlockAnchored
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.gtv.merkleHash
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXOpMistake
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.Gtx
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.ExtOpData

class IccfGTXOperation(
        iccfContext: IccfGTXModuleContext,
        opData: ExtOpData
) : GTXOperation(opData) {

    companion object {
        const val ICCF_OP_NAME = "iccf_proof"

        val metadata = OperationMetadata(args = listOf(
                ArgumentMetadata("blockchain_rid", setOf(GtvType.BYTEARRAY)),
                ArgumentMetadata("tx_hash", setOf(GtvType.BYTEARRAY)),
                ArgumentMetadata("tx_proof", setOf(GtvType.BYTEARRAY)),
                ArgumentMetadata("raw_cluster_anchoring_tx", setOf(GtvType.BYTEARRAY), required = false),
                ArgumentMetadata("cluster_anchoring_tx_op_index", setOf(GtvType.INTEGER), required = false),
                ArgumentMetadata("cluster_anchoring_tx_proof", setOf(GtvType.BYTEARRAY), required = false),
        ))
    }

    private val cryptoSystem = iccfContext.cryptoSystem
    private val clusterManagement = iccfContext.clusterManagement
    private val nodeManagement = iccfContext.nodeManagement
    private val queryProvider = iccfContext.queryProvider
    private val nodeIsReplica = iccfContext.nodeIsReplica
    private val myCluster = clusterManagement.getClusterOfBlockchain(opData.blockchainRID)

    override fun apply(ctx: TxEContext) = true

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing() {
        verifyIccf(data.args, true)
    }

    override fun checkCorrectness() {
        verifyIccf(data.args, false)
    }

    private fun verifyIccf(args: Array<out Gtv>, isSyncing: Boolean) {
        if (!isSyncing) {
            if (data.operations
                            .filter { it.opName == ICCF_OP_NAME }
                            .groupingBy { op ->
                                val sourceBlockchainRid = decodeSafely(op.args, 0) { it.asByteArray() }
                                val sourceTxHash = decodeSafely(op.args, 1) { it.asByteArray() }
                                (sourceBlockchainRid + sourceTxHash).contentHashCode()
                            }
                            .eachCount()
                            .any { it.value > 1 }) {
                throw GTXOpMistake("Duplicate $ICCF_OP_NAME operation detected", data)
            }
        }

        when (args.size) {
            3 -> verifyIntraClusterIccf(args)
            6 -> verifyIntraNetworkIccf(args, isSyncing)
            else -> {
                throw GTXOpMistake("Wrong number of arguments", data)
            }
        }
    }

    private fun verifyIntraClusterIccf(args: Array<out Gtv>) {
        val (sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid, sourceBlockHeaderData) = getSourceInfo(args)
        val sourceIsRemoved = nodeManagement.getBlockchainState(sourceBlockchainRid) == BlockchainState.REMOVED

        // This can't be verified if chain is removed but anchoring check will fail anyway so that's fine
        // It's still worth doing this check if not removed (to save time and get a better error message)
        if (!sourceIsRemoved) {
            verifySourceChainInSameClusterAsTargetChain(sourceBlockchainRid)
        }
        verifyWitnessesAndMerkleProofTree(sourceTxConfirmationProof, sourceBlockchainRid, sourceBlockRid, sourceTxHash, sourceBlockHeaderData)
        verifySourceBlockAnchoredInClusterAnchoringChain(sourceBlockchainRid, sourceBlockRid)
    }

    private fun verifyIntraNetworkIccf(args: Array<out Gtv>, isSyncing: Boolean) {
        val (sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid, sourceBlockHeaderData) = getSourceInfo(args)
        val sourceIsRemoved = nodeManagement.getBlockchainState(sourceBlockchainRid) == BlockchainState.REMOVED

        // This is not acceptable since we can't verify that anchoring proof is actually from the correct anchoring chain
        // If we are syncing we have no option but to accept this limitation
        if (sourceIsRemoved && !isSyncing) {
            throw UserMistake("Source blockchain is removed")
        }

        verifyWitnessesAndMerkleProofTree(sourceTxConfirmationProof, sourceBlockchainRid, sourceBlockRid, sourceTxHash, sourceBlockHeaderData)

        val rawClusterAnchoringTx = decodeSafely(args, 3) { it.asByteArray() }
        val clusterAnchoringTxOpIndex = decodeSafely(args, 4) { it.asInteger() }.toInt()
        val clusterAnchoringTxProof = GtvDecoder.decodeGtv(decodeSafely(args, 5) { it.asByteArray() })
        val clusterAnchoringTxGtv = GtvFactory.decodeGtv(rawClusterAnchoringTx)
        val clusterAnchoringTx = Gtx.fromGtv(clusterAnchoringTxGtv)

        if (!sourceIsRemoved) {
            verifyAnchoringProofIsFromCorrectClusterAnchoringChain(sourceBlockchainRid, clusterAnchoringTx)
        }
        verifySourceBlockAnchoringOperationIsPresentInClusterAnchoringTX(clusterAnchoringTx, clusterAnchoringTxOpIndex, sourceBlockRid, sourceTxConfirmationProof)

        val clusterAnchoringTxConfirmationProof = GtvObjectMapper.fromGtv(clusterAnchoringTxProof, ConfirmationProof::class.java)
        val clusterAnchoringBlockHeaderGtv = GtvDecoder.decodeGtv(clusterAnchoringTxConfirmationProof.blockHeader)
        val clusterAnchoringBlockHeaderData = BlockHeaderData.fromGtv(clusterAnchoringBlockHeaderGtv)
        val merkleHashCalculater = makeMerkleHashCalculator(clusterAnchoringBlockHeaderData.getMerkleHashVersion())

        val clusterAnchoringTxHash = clusterAnchoringTxGtv.merkleHash(merkleHashCalculater)
        val clusterAnchoringBlockRid = clusterAnchoringBlockHeaderGtv.merkleHash(merkleHashCalculater)

        verifyWitnessesAndMerkleProofTree(clusterAnchoringTxConfirmationProof, clusterAnchoringTx.gtxBody.blockchainRid, clusterAnchoringBlockRid, clusterAnchoringTxHash, clusterAnchoringBlockHeaderData)
        verifyClusterAnchoringBlockExistsInSystemAnchoringChain(clusterAnchoringTx, clusterAnchoringBlockRid)
    }

    private fun verifyWitnessesAndMerkleProofTree(confirmationProof: ConfirmationProof, blockchainRid: BlockchainRid, blockRid: Hash, txHash: ByteArray, blockHeaderData: BlockHeaderData) {
        verifyWitnessesAreCorrectAllowedWitnesses(blockchainRid, blockHeaderData, confirmationProof, blockRid)
        verifyMerkleProofTree(confirmationProof, blockHeaderData, txHash)
    }

    private fun verifyMerkleProofTree(confirmationProof: ConfirmationProof, decodedBlockHeader: BlockHeaderData, txHash: ByteArray) {
        val merkleHashCalculater = makeMerkleHashCalculator(decodedBlockHeader.getMerkleHashVersion())
        val proofRootHash = confirmationProof.merkleProofTree.merkleHash(merkleHashCalculater)
        if (!decodedBlockHeader.getMerkleRootHash().contentEquals(proofRootHash)) {
            throw UserMistake("Proof tree root hash mismatch, expected ${decodedBlockHeader.getMerkleRootHash().toHex()} but was ${proofRootHash.toHex()}")
        }

        val proofTxHash = confirmationProof.merkleProofTree.toGtvVirtual()[confirmationProof.txIndex.toInt()].asByteArray()
        if (!txHash.contentEquals(proofTxHash)) {
            throw UserMistake("Proof transaction hash mismatch, expected ${txHash.toHex()} but was ${proofTxHash.toHex()}")
        }
    }

    private fun verifyWitnessesAreCorrectAllowedWitnesses(blockchainRid: BlockchainRid, decodedBlockHeader: BlockHeaderData, confirmationProof: ConfirmationProof, blockRid: Hash) {
        // Ask chain0 about signers at height (BlockQueriesProvider)
        val signers = clusterManagement.getBlockchainPeers(blockchainRid, decodedBlockHeader.getHeight())
        // Verify witnesses has correctly signed block -> Root hash is correct in block header
        Validation.validateBlockSignatures(
                cryptoSystem,
                decodedBlockHeader.getPreviousBlockRid(),
                confirmationProof.blockHeader,
                blockRid,
                signers,
                confirmationProof.witness
        )
    }

    private fun verifySourceChainInSameClusterAsTargetChain(sourceBlockchainRid: BlockchainRid) {
        val sourceCluster = clusterManagement.getClusterOfBlockchain(sourceBlockchainRid)
        if (myCluster != sourceCluster) {
            throw UserMistake("Source blockchain is not in our cluster but no cluster anchoring proof was supplied.")
        }
    }

    private fun verifySourceBlockAnchoredInClusterAnchoringChain(sourceBlockchainRid: BlockchainRid, sourceBlockRid: ByteArray) {
        // Since replica nodes may not run cluster anchoring chain they are allowed to skip this check
        if (!nodeIsReplica) {
            val clusterAnchoringQuery = queryProvider.getClusterAnchoringQuery()
                    ?: throw ProgrammerMistake("Unable to get cluster anchoring queries")
            if (!clusterAnchoringQuery.isBlockAnchored(sourceBlockchainRid, sourceBlockRid)) {
                throw UserMistake("Source block is not anchored in cluster anchoring chain")
            }
        }
    }

    private fun verifyAnchoringProofIsFromCorrectClusterAnchoringChain(sourceBlockchainRid: BlockchainRid, clusterAnchoringTx: Gtx) {
        val sourceCluster = clusterManagement.getClusterOfBlockchain(sourceBlockchainRid)
        if (clusterAnchoringTx.gtxBody.blockchainRid != clusterManagement.getClusterInfo(sourceCluster).anchoringChain) {
            throw UserMistake("Cluster anchoring tx is not from the cluster anchoring chain of source cluster: $sourceCluster")
        }
    }

    private fun verifySourceBlockAnchoringOperationIsPresentInClusterAnchoringTX(clusterAnchoringTx: Gtx, clusterAnchoringTxOpIndex: Int, sourceBlockRid: Hash, sourceTxConfirmationProof: ConfirmationProof) {
        val clusterAnchoringTxOperations = clusterAnchoringTx.gtxBody.operations

        val batchOp = clusterAnchoringTxOperations.find { it.opName == AnchoringSpecialTxExtension.OP_BATCH_BLOCK_HEADER }
        if (batchOp != null) {
            val batchAnchoringBlocks = batchOp.args[0].asArray()
            val anchorBlockElement = if (batchAnchoringBlocks.size >= clusterAnchoringTxOpIndex + 1) {
                batchAnchoringBlocks[clusterAnchoringTxOpIndex]
            } else {
                throw UserMistake("Invalid index in cluster anchoring batch operation")
            }

            if (!sourceBlockRid.contentEquals(anchorBlockElement[0].asByteArray())
                    || !sourceTxConfirmationProof.blockHeader.contentEquals(GtvEncoder.encodeGtv(anchorBlockElement[1]))
            ) {
                throw UserMistake("No source block anchoring operation is present in anchoring TX")
            }
        } else {
            val anchoringTxOp = if (clusterAnchoringTxOperations.size >= clusterAnchoringTxOpIndex + 1) {
                clusterAnchoringTxOperations[clusterAnchoringTxOpIndex]
            } else {
                throw UserMistake("Invalid operation index in cluster anchoring TX")
            }
            if (anchoringTxOp.opName != AnchoringSpecialTxExtension.OP_BLOCK_HEADER
                    || !sourceBlockRid.contentEquals(anchoringTxOp.args[0].asByteArray())
                    || !sourceTxConfirmationProof.blockHeader.contentEquals(GtvEncoder.encodeGtv(anchoringTxOp.args[1]))
            ) {
                throw UserMistake("No source block anchoring operation is present in anchoring TX")
            }
        }
    }

    private fun verifyClusterAnchoringBlockExistsInSystemAnchoringChain(clusterAnchoringTx: Gtx, clusterAnchoringBlockRid: ByteArray) {
        val systemAnchoringQuery = queryProvider.getSystemAnchoringQuery()
        if (systemAnchoringQuery != null) {
            if (!systemAnchoringQuery.isBlockAnchored(clusterAnchoringTx.gtxBody.blockchainRid, clusterAnchoringBlockRid)) {
                throw UserMistake("Cluster anchoring block is not anchored in system anchoring chain")
            }
        }
    }

    private fun getSourceInfo(args: Array<out Gtv>): SourceInfo {
        val sourceBlockchainRid = BlockchainRid(decodeSafely(args, 0) { it.asByteArray() })
        val sourceTxHash = decodeSafely(args, 1) { it.asByteArray() }
        val sourceTxProof = GtvDecoder.decodeGtv(decodeSafely(args, 2) { it.asByteArray() })
        val sourceTxConfirmationProof = GtvObjectMapper.fromGtv(sourceTxProof, ConfirmationProof::class.java)

        val sourceBlockHeaderGtv = GtvDecoder.decodeGtv(sourceTxConfirmationProof.blockHeader)
        val sourceBlockHeaderData = BlockHeaderData.fromGtv(sourceBlockHeaderGtv)
        val gtvMerkleHashCalculator = makeMerkleHashCalculator(sourceBlockHeaderData.getMerkleHashVersion())

        val sourceBlockRid = sourceBlockHeaderGtv.merkleHash(gtvMerkleHashCalculator)
        return SourceInfo(sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid, sourceBlockHeaderData)
    }

    private fun <T> decodeSafely(args: Array<out Gtv>, argIndex: Int, decodeFn: (Gtv) -> T): T {
        return try {
            decodeFn(args[argIndex])
        } catch (e: UserMistake) {
            throw GTXOpMistake("Wrong argument type", data, argIndex, e)
        }
    }

    @Suppress("ArrayInDataClass")
    private data class SourceInfo(
            val sourceBlockchainRid: BlockchainRid,
            val sourceTxHash: ByteArray,
            val sourceTxConfirmationProof: ConfirmationProof,
            val sourceBlockRid: Hash,
            val sourceBlockHeader: BlockHeaderData
    )
}
