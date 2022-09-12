package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandRevokeProposal : CliktCommand(
    name = "revoke",
    help = "Revoke a given proposal"
) {
    private val config by configOption()
    private val idx by proposalIndexOption().required()

    override fun run() {
        CliExecutionD1(config).revokeProposal(idx)
    }
}
