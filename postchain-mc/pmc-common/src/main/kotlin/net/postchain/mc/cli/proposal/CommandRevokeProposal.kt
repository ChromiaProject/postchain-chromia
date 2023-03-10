package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.proposal.revokeProposalOperation
import net.postchain.common.types.RowId
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.proposal.util.proposalIndexOption
import net.postchain.mc.cli.util.nopClientOption

class CommandRevokeProposal : CliktCommand(
        name = "revoke",
        help = "Revoke/remove a proposal submitted by you"
) {
    private val client by nopClientOption()
    private val idx by proposalIndexOption().required()

    override fun run() {
        client.client.transactionBuilder()
                .revokeProposalOperation(client.pubkey, RowId(idx))
                .postAwaitConfirmation()
                .printResult(
                        "Proposal revoked successfully",
                        "Cannot revoke proposal"
                )
    }
}
