package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.addBlockchainReplicaOperation
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandAddBlockchainReplica : CliktCommand(
        name = "add",
        help = "add replica of a blockchain. The node is verifying but not building blocks."
) {

    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        client.transactionBuilder()
                .addBlockchainReplicaOperation(
                        client.pubkey,
                        blockchainRID,
                        key.hexStringToByteArray()
                )
                .postSyncAwaitConfirmation()
                .printResult(
                        "Replica added",
                        "Cannot add replica"
                )
    }

}