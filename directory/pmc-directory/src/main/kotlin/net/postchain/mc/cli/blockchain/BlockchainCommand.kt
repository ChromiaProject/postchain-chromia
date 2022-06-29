package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.mc.cli.proposal.CommandGetProposal
import net.postchain.mc.cli.proposal.CommandListProposalsSince
import net.postchain.mc.cli.proposal.CommandVote
import net.postchain.mc.cli.proposal.ProposalCommand

class BlockchainCommand : CliktCommand("Interactions with blockchains") {
    override fun run() = Unit
}

fun blockchainCommands() = BlockchainCommand().subcommands(
    CommandProposeBlockchain(),
    CommandProposeDeleteBlockchain(),
    CommandProposePauseBlockchain(),
    CommandProposeUnPauseBlockchain(),
    CommandListBlockchainReplicas(),
    CommandListBlockchainSigners(),
    CommandListBlockchains(),
    CommandGetBlockchainConfiguration()
)