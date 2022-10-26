package net.postchain.mc.cli.replica

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class ReplicaCommand : NoOpCliktCommand("Blockchain replica commands")

fun blockchainReplicaCommands() = ReplicaCommand().subcommands(
        CommandAddBlockchainReplica(),
        CommandRemoveBlockchainReplica()
)