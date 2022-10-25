package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.proposal.proposeBlockchainActionOperation
import net.postchain.chain0.model.BlockchainAction
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandProposePauseBlockchain : CliktCommand(
        name = "stop",
        help = "Propose stopping a blockchain from building blocks"
) {
    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        client.transactionBuilder()
                .proposeBlockchainActionOperation(
                    client.config.pubkey().wData,
                    blockchainRID,
                    BlockchainAction.pause
                )
                .postSyncAwaitConfirmation()
                .printResult(
                        "Blockchain pause proposition was added successfully",
                        "Cannot add proposal for pausing blockchain"
                )
    }
}