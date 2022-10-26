package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeRemoveClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeRemoveCluster : CliktCommand(
        name = "remove",
        help = "Propose removal of cluster. Command is irreversible"
) {
    private val client by nopClientOption()

    private val name by nameOption("Cluster name to remove").required()

    override fun run() {
        client.transactionBuilder()
                .proposeRemoveClusterOperation(client.config.pubkey().wData, name)
                .postSyncAwaitConfirmation()
                .printResult(
                        "Cluster removal proposed",
                        "Failed proposing cluster removal"
                )
    }
}