package net.postchain.mc.cli.base

import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.getLastProposal
import net.postchain.client.core.*
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.SigMaker
import net.postchain.mc.config.app.ClientConfig

object ClientUtil {

    fun fromConfig(config: ClientConfig): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty()) {
            throw UserMistake("Missing required parameters: brid | pub-key | priv-key")
        }
        val sigMaker = sigMaker(config)
        val defaultSigner = DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray())

        return ConcretePostchainClientProvider().createClient(config.apiURL, BlockchainRid.buildFromHex(config.brid), defaultSigner)
    }

    fun sigMaker(config: ClientConfig): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }

}

fun PostchainClient.sendTxWithNop(sigMaker: SigMaker, op: GTXTransactionBuilder.() -> Unit) {
    makeTransaction().apply {
        addNop()
        op(this)
        sign(sigMaker)
        postSync(ConfirmationLevel.UNVERIFIED)
    }
}

/**
 * Sends a tx and returns the last created proposal id
 */
fun PostchainClient.createProposal(proposalType: ProposalType, myKey: String, sigMaker: SigMaker, op: GTXTransactionBuilder.() -> Unit): Long? {
    sendTxWithNop(sigMaker, op)
    return getLastProposal(proposalType, myKey.hexStringToByteArray())
}
