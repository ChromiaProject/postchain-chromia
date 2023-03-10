package net.postchain.d1.iccf

import net.postchain.chain0.anchoring_chain_common.getAnchoringTransactionForBlockRid
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TxRid
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.Gtx

class IccfProofTxMaterialBuilder(private val chromiaClientProvider: ChromiaClientProvider) {
    private val cryptoSystem = chromiaClientProvider.cryptoSystem
    private val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val clusterManagement = chromiaClientProvider.clusterManagement

    fun build(
            txToProveRID: TxRid,
            txToProveHash: Hash,
            txToProveSigners: List<PubKey>,
            sourceBlockchainRid: BlockchainRid,
            targetBlockchainRid: BlockchainRid,
            forceIntraNetworkIccfOperation: Boolean = false
    ): IccfProofTxMaterial {
        val sourceClient = chromiaClientProvider.blockchain(sourceBlockchainRid)
        val txProof = sourceClient.confirmationProof(txToProveRID)
                ?: throw UserMistake("Failed to get confirmation proof")
        val decodedProof = GtvDecoder.decodeGtv(txProof)
        val proofHash = decodedProof["hash"]?.asByteArray()

        val (updatedTx, sourceTxHash) = if (!txToProveHash.contentEquals(proofHash)) {
            verifyUpdatedHash(sourceClient, txToProveRID, proofHash, txToProveSigners)
        } else {
            null to txToProveHash
        }

        val targetClient = chromiaClientProvider.blockchain(targetBlockchainRid)
        val txBuilder = targetClient.transactionBuilder()

        val sourceCluster = clusterManagement.getClusterOfBlockchain(sourceBlockchainRid)
        val targetCluster = clusterManagement.getClusterOfBlockchain(targetBlockchainRid)
        if (!forceIntraNetworkIccfOperation && sourceCluster == targetCluster) { // intra-cluster
            addTransactionProofOperation(txBuilder, sourceBlockchainRid, sourceTxHash, txProof)
        } else { // intra-network
            addAnchoredProofOperation(txProof, decodedProof, sourceCluster, sourceBlockchainRid, txBuilder, sourceTxHash)
        }
        return IccfProofTxMaterial(txBuilder, updatedTx)
    }

    private fun verifyUpdatedHash(sourceClient: PostchainClient, txToProveRID: TxRid, proofHash: ByteArray?, txToProveSigners: List<PubKey>): Pair<Gtx, Hash> {
        // Fetch full tx and investigate why we have a mismatch
        val rawTx = sourceClient.getTransaction(txToProveRID)
                ?: throw ProgrammerMistake("Failed to fetch transaction for verification")
        val txGtv = GtvDecoder.decodeGtv(rawTx)
        val fetchedTxHash = txGtv.merkleHash(hashCalculator)
        if (!fetchedTxHash.contentEquals(proofHash)) {
            // We received another hash for tx RID than what was included in proof
            // Possibly rouge or faulty node(s). Anyway, we need to give up.
            throw ProgrammerMistake("Unable to verify source transaction proof, " +
                    "transaction hash in proof ${proofHash?.toHex()} does not match hash from fetched transaction ${fetchedTxHash.toHex()}"
            )
        }

        val tx = Gtx.fromGtv(txGtv)
        if (txToProveSigners.size != tx.signatures.size) {
            throw UserMistake("Transaction signatures amount ${tx.signatures.size} do not match expected amount of signers ${txToProveSigners.size}")
        }

        val txRid = tx.calculateTxRid(hashCalculator)
        if (!txRid.contentEquals(txToProveRID.rid.hexStringToByteArray())) {
            // We received a tx with a different RID than we asked for. Rouge or faulty node(s).
            throw ProgrammerMistake("Unable to verify source transaction proof, got a different transaction from query than we asked for")
        }

        for (signer in txToProveSigners) {
            var hasSignature = false

            // Signatures may have been re-ordered or reformatted
            for (signature in tx.signatures) {
                if (cryptoSystem.verifyDigest(txRid, Signature(signer.data, signature))) {
                    hasSignature = true
                    break
                }
            }
            if (!hasSignature) throw UserMistake("Expected signer $signer has not signed source transaction")
        }

        return tx to fetchedTxHash
    }

    private fun addTransactionProofOperation(txBuilder: TransactionBuilder, sourceBlockchainRid: BlockchainRid, txHash: Hash, txProof: ByteArray) {
        txBuilder.addOperation(
                ICCF_OP_NAME,
                GtvFactory.gtv(sourceBlockchainRid.data),
                GtvFactory.gtv(txHash),
                GtvFactory.gtv(txProof)
        )
    }

    private fun addAnchoredProofOperation(txProof: ByteArray, decodedTxProof: Gtv, sourceCluster: String, sourceBlockchainRid: BlockchainRid, txBuilder: TransactionBuilder, txHash: Hash) {
        val sourceBlockHeader = decodedTxProof["blockHeader"]?.asByteArray()
                ?: throw UserMistake("Failed to get blockHeader from confirmation proof")
        val sourceBlockRid = GtvDecoder.decodeGtv(sourceBlockHeader).merkleHash(hashCalculator)
        // Get proof for ac
        val anchoringChainRid = clusterManagement.getClusterInfo(sourceCluster).anchoringChain
        val anchoringChainClient = chromiaClientProvider.blockchain(anchoringChainRid)

        val anchoringTx = anchoringChainClient.getAnchoringTransactionForBlockRid(sourceBlockchainRid, sourceBlockRid)
                ?: throw UserMistake("Block is not present in cluster anchoring chain")
        val anchoringProof = anchoringChainClient.confirmationProof(TxRid(anchoringTx.txRid.toHex()))
                ?: throw UserMistake("Failed to get confirmation proof for anchoring chain")

        txBuilder.addOperation(
                ICCF_OP_NAME,
                GtvFactory.gtv(sourceBlockchainRid.data),
                GtvFactory.gtv(txHash),
                GtvFactory.gtv(txProof),
                GtvFactory.gtv(anchoringTx.txData),
                GtvFactory.gtv(anchoringTx.txOpIndex),
                GtvFactory.gtv(anchoringProof)
        )
    }

    companion object {
        const val ICCF_OP_NAME = "iccf_proof"
    }
}
