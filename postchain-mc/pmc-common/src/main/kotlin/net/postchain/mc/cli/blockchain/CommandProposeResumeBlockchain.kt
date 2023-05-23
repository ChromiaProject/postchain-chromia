package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.blockchainRidOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption

class CommandProposeResumeBlockchain : CliktCommand(
        name = "start",
        help = "Propose starting a chain that has previously been stopped."
) {
    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    private val description by proposalDescriptionOption()

    override fun run() {
        client.transactionBuilder()
                .proposeBlockchainActionOperation(
                        client.config.pubkey().data,
                        blockchainRID,
                        BlockchainAction.resume,
                        description
                )
                .postAwaitConfirmation()
                .printResult(
                        "Blockchain resume proposition was added successfully",
                        "Cannot add proposal for resuming blockchain"
                )
    }
}