package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.removeBlockchainReplicaOperation
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandRemoveBlockchainReplica : CliktCommand(
        name = "remove",
        help = "remove replica of a blockchain"
) {
    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        client.transactionBuilder()
                .removeBlockchainReplicaOperation(
                        client.pubkey,
                        blockchainRID,
                        key.data
                )
                .postAwaitConfirmation()
                .printResult(
                        "Replica removed",
                        "Cannot remove replica node"
                )
    }
}