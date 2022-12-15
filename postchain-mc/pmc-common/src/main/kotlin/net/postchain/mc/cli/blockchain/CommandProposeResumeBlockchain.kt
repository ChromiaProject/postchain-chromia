package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.proposal.proposeBlockchainActionOperation
import net.postchain.chain0.model.BlockchainAction
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeResumeBlockchain : CliktCommand(
        name = "start",
        help = "Propose starting a chain that has previously been stopped."
) {
    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        client.transactionBuilder()
                .proposeBlockchainActionOperation(
                        client.config.pubkey().data,
                        blockchainRID,
                        BlockchainAction.resume
                )
                .postAwaitConfirmation()
                .printResult(
                        "Blockchain resume proposition was added successfully",
                        "Cannot add proposal for resuming blockchain"
                )
    }
}