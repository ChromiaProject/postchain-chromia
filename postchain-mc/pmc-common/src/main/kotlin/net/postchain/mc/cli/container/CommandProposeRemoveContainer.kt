package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeRemoveContainerOperation
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeRemoveContainer : CliktCommand(
        name = "remove",
        help = "Propose removal of container. Command is irreversible"
) {
    private val config by configOption()

    private val name by nameOption("Container name").required()

    override fun run() {
        ClientUtil.nopClientFromConfig(config)
                .transactionBuilder()
                .proposeRemoveContainerOperation(config.pubkey().key, name)
                .postSyncAwaitConfirmation()
                .printResult(
                        "Container removal proposed",
                        "Failed proposing container removal"
                )
    }
}