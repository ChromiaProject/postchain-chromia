package net.postchain.mc.cli.cluster.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import net.postchain.chain0.common.cluster.removeReplicaNodeFromClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.validateAlphaNumeric

class CommandRemoveClusterReplica : CliktCommand(
        name = "remove",
        help = "remove replica of a cluster"
) {
    private val client by nopClientOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    private val key by requiredPubkeyOption()

    override fun run() {
        client.transactionBuilder()
                .removeReplicaNodeFromClusterOperation(
                        client.pubkey, key.data, name
                )
                .postAwaitConfirmation()
                .printResult(
                        "Cluster replica removed",
                        "Cannot remove cluster replica node"
                )
    }
}