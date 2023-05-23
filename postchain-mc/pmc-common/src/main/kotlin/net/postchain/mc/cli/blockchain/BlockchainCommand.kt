package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.mc.cli.replica.blockchainReplicaCommands

class BlockchainCommand : CliktCommand("Interactions with blockchains") {
    override fun run() = Unit
}

fun blockchainCommands() = BlockchainCommand().subcommands(
    CommandProposeBlockchain(),
    CommandProposeConfiguration(),
    CommandProposePauseBlockchain(),
    CommandProposeResumeBlockchain(),
    CommandListBlockchainReplicas(),
    CommandListBlockchainSigners(),
    CommandListBlockchains(),
    CommandGetBlockchainConfiguration(),
    blockchainReplicaCommands()
)