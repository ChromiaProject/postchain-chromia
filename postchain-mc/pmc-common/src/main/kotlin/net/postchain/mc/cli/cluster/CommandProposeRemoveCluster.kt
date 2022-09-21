package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeRemoveClusterOperation
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeRemoveCluster : CliktCommand(
        name = "remove",
        help = "Propose removal of cluster. Command is irreversible"
) {
    private val config by configOption()

    private val name by nameOption("Cluster name").required()

    override fun run() {
        ClientUtil.nopClientFromConfig(config)
                .transactionBuilder()
                .proposeRemoveClusterOperation(config.pubkey().key, name)
                .postSyncAwaitConfirmation()
                .printResult(
                        "Cluster removal proposed",
                        "Failed proposing cluster removal"
                )
    }
}