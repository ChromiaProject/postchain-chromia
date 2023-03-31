package net.postchain.mc.cli.cluster.replica

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import net.postchain.chain0.common.operations.addReplicaNodeToClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption
import net.postchain.mc.cli.util.validateAlphaNumeric

class CommandAddClusterReplica : CliktCommand(
        name = "add",
        help = "add replica of a cluster"
) {

    private val client by nopClientOption()

    private val name by nameOption("Cluster Name").required().validate(validateAlphaNumeric())

    private val nodePubKey by pubkeyOption().required()

    override fun run() {
        client.transactionBuilder()
                .addReplicaNodeToClusterOperation(
                        client.pubkey, nodePubKey.data, name
                )
                .postAwaitConfirmation()
                .printResult(
                        "Cluster replica added",
                        "Cannot add cluster replica"
                )
    }

}