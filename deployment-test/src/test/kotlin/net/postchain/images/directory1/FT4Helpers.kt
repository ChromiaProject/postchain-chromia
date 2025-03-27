package net.postchain.images.directory1

import net.postchain.chain0.economy_chain_test_auth_server.registerAccountOperation
import net.postchain.chain0.lib.ft4.external.crosschain.APPLY_TRANSFER
import net.postchain.chain0.lib.ft4.external.crosschain.COMPLETE_TRANSFER
import net.postchain.chain0.lib.ft4.external.crosschain.initTransferOperation
import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PubKey
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import java.math.BigInteger


fun registerAccount(
        node: PostchainContainer,
        ecAdminKeyPair: KeyPair,
        blockchainRid: BlockchainRid, userKeyPair: KeyPair, username: String
): FTAuthenticator {
    node.client(blockchainRid, listOf(ecAdminKeyPair)).transactionBuilder().addNop()
            .registerAccountOperation(userKeyPair.pubKey)
            .postTransactionUntilConfirmed("Register $username account")

    return FTAuthenticator(userKeyPair, node.client(blockchainRid, listOf(userKeyPair)))
}

fun performCrossChainTransfer(
        node: PostchainContainer,
        iccfProofTxMaterialBuilder: IccfProofTxMaterialBuilder,
        hashCalculator: GtvMerkleHashCalculatorV2,
        sourceAccountAuthenticator: FTAuthenticator,
        sourceChain: BlockchainRid,
        destinationChain: BlockchainRid,
        amount: BigInteger = BigInteger.TEN,
        assetId: ByteArray,
) {
    val initTransferTxRid = sourceAccountAuthenticator.transactionBuilder()
            .initTransferOperation(sourceAccountAuthenticator.accountId, assetId, amount, listOf(destinationChain.data), Long.MAX_VALUE)
            .postAwaitConfirmation().txRid

    val initTransferTx = GtvDecoder.decodeGtv(node.client(sourceChain).getTransaction(initTransferTxRid))

    val initTxProof = awaitQueryResult {
        iccfProofTxMaterialBuilder.build(
                initTransferTxRid,
                initTransferTx.merkleHash(hashCalculator),
                sourceChain,
                destinationChain,
                forceIntraNetworkIccfOperation = true
        )
    }!!

    val applyTransferTxRid = initTxProof.txBuilder
            .addOperation(APPLY_TRANSFER, initTransferTx, gtv(1), initTransferTx, gtv(1), gtv(0))
            .postTransactionUntilConfirmed("Apply transfer tx").txRid

    val applyTransferTx = awaitQueryResult {
        GtvDecoder.decodeGtv(node.client(destinationChain).getTransaction(applyTransferTxRid))
    }!!

    val applyTxProof = awaitQueryResult {
        iccfProofTxMaterialBuilder.build(
                applyTransferTxRid,
                applyTransferTx.merkleHash(hashCalculator),
                destinationChain,
                sourceChain,
                forceIntraNetworkIccfOperation = true
        )
    }!!

    applyTxProof.txBuilder
            .addOperation(COMPLETE_TRANSFER, applyTransferTx, gtv(1))
            .postAwaitConfirmation()

}
