package net.postchain.d1.iccf

import net.postchain.base.ConfirmationProof
import net.postchain.base.gtv.BlockHeaderData
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
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXOpMistake
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.Gtx
import net.postchain.gtx.data.ExtOpData

class IccfGTXOperation(
        iccfContext: IccfGTXModuleContext,
        opData: ExtOpData
) : GTXOperation(opData) {
    private val cryptoSystem = iccfContext.cryptoSystem
    private val clusterManagement = iccfContext.clusterManagement
    private val queryProvider = iccfContext.queryProvider
    private val nodeIsReplica = iccfContext.nodeIsReplica
    private val gtvMerkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val myCluster = clusterManagement.getClusterOfBlockchain(opData.blockchainRID)

    override fun apply(ctx: TxEContext) = true

    override fun isCorrect(): Boolean {
        val args = data.args
        return when (args.size) {
            3 -> verifyIntraClusterIccf(args)
            6 -> verifyIntraNetworkIccf(args)
            else -> {
                throw GTXOpMistake("Wrong number of arguments", data)
            }
        }
    }

    private fun verifyIntraClusterIccf(args: Array<out Gtv>): Boolean {
        val (sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid) = getSourceInfo(args)
        verifySourceChainInSameClusterAsTargetChain(sourceBlockchainRid)
        verifyWitnessesAndMerkleProofTree(sourceTxConfirmationProof, sourceBlockchainRid, sourceBlockRid, sourceTxHash)
        verifySourceBlockAnchoredInClusterAnchoringChain(sourceBlockchainRid, sourceBlockRid)
        return true
    }

    private fun verifyIntraNetworkIccf(args: Array<out Gtv>): Boolean {
        val (sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid) = getSourceInfo(args)
        verifyWitnessesAndMerkleProofTree(sourceTxConfirmationProof, sourceBlockchainRid, sourceBlockRid, sourceTxHash)

        val rawClusterAnchoringTx = decodeSafely(args, 3) { it.asByteArray() }
        val clusterAnchoringTxOpIndex = decodeSafely(args, 4) { it.asInteger() }.toInt()
        val clusterAnchoringTxProof = GtvDecoder.decodeGtv(decodeSafely(args, 5) { it.asByteArray() })
        val clusterAnchoringTxGtv = GtvFactory.decodeGtv(rawClusterAnchoringTx)
        val clusterAnchoringTx = Gtx.fromGtv(clusterAnchoringTxGtv)

        verifyAnchoringProofIsFromCorrectClusterAnchoringChain(sourceBlockchainRid, clusterAnchoringTx)
        verifySourceBlockAnchoringOperationIsPresentInClusterAnchoringTX(clusterAnchoringTx, clusterAnchoringTxOpIndex, sourceBlockRid, sourceTxConfirmationProof)

        val clusterAnchoringTxHash = clusterAnchoringTxGtv.merkleHash(gtvMerkleHashCalculator)
        val clusterAnchoringTxConfirmationProof = GtvObjectMapper.fromGtv(clusterAnchoringTxProof, ConfirmationProof::class.java)
        val clusterAnchoringBlockRid = GtvDecoder.decodeGtv(clusterAnchoringTxConfirmationProof.blockHeader).merkleHash(gtvMerkleHashCalculator)

        verifyWitnessesAndMerkleProofTree(clusterAnchoringTxConfirmationProof, clusterAnchoringTx.gtxBody.blockchainRid, clusterAnchoringBlockRid, clusterAnchoringTxHash)
        verifyClusterAnchoringBlockExistsInSystemAnchoringChain(clusterAnchoringTx, clusterAnchoringBlockRid)

        return true
    }

    private fun verifyWitnessesAndMerkleProofTree(confirmationProof: ConfirmationProof, blockchainRid: BlockchainRid, blockRid: Hash, txHash: ByteArray) {
        val decodedBlockHeader = BlockHeaderData.fromBinary(confirmationProof.blockHeader)
        verifyWitnessesAreCorrectAllowedWitnesses(blockchainRid, decodedBlockHeader, confirmationProof, blockRid)
        verifyMerkleProofTree(confirmationProof, decodedBlockHeader, txHash)
    }

    private fun verifyMerkleProofTree(confirmationProof: ConfirmationProof, decodedBlockHeader: BlockHeaderData, txHash: ByteArray) {
        val proofRootHash = confirmationProof.merkleProofTree.merkleHash(gtvMerkleHashCalculator)
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
        val sourceBlockRid = GtvDecoder.decodeGtv(sourceTxConfirmationProof.blockHeader).merkleHash(gtvMerkleHashCalculator)
        return SourceInfo(sourceBlockchainRid, sourceTxHash, sourceTxConfirmationProof, sourceBlockRid)
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
            val sourceBlockRid: Hash
    )
}
