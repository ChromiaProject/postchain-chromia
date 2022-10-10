package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.model.BlockchainAction
import net.postchain.chain0.common.proposal.proposeBlockchainActionOperation
import net.postchain.cli.util.blockchainRidOption
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeDeleteBlockchain : CliktCommand(
        name = "remove",
        help = "Propose removal of blockchain. Command is irreversible"
) {
    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    override fun run() {
        client.transactionBuilder()
                .proposeBlockchainActionOperation(
                        client.config.pubkey().key,
                        blockchainRID.data,
                        BlockchainAction.remove
                )
                .postSyncAwaitConfirmation()
                .printResult(
                        "Blockchain delete proposition was added successfully",
                        "Cannot add proposal for deleting blockchain"
                )
    }
}