package net.postchain.mc.cli.proposal

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class ProposalCommand : CliktCommand("Interact with existing proposals") {
    override fun run() = Unit
}

fun proposalCommands() = ProposalCommand().subcommands(
    CommandGetProposal(),
    CommandListProposals(),
    CommandRevokeProposal(),
    CommandVote()
)