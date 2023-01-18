package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.addBlockchainReplicaOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.blockchainRidOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption

class CommandAddBlockchainReplica : CliktCommand(
        name = "add",
        help = "add replica of a blockchain. The node is verifying but not building blocks."
) {

    private val client by nopClientOption()

    private val blockchainRID by blockchainRidOption()

    private val nodePubKey by pubkeyOption().required()

    override fun run() {
        client.transactionBuilder()
                .addBlockchainReplicaOperation(
                        client.pubkey,
                        blockchainRID,
                        nodePubKey.data
                )
                .postAwaitConfirmation()
                .printResult(
                        "Replica added",
                        "Cannot add replica"
                )
    }

}