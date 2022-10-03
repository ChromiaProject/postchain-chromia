package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.configOption

class CommandRevokeProposal : CliktCommand(
    name = "revoke",
    help = "Revoke a given proposal"
) {
    private val config by configOption()
    private val idx by proposalIndexOption().required()

    override fun run() {
        CliExecution(config).revokeProposal(idx)
    }
}
