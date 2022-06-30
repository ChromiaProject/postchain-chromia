package net.postchain.mc

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.cli.*
import net.postchain.mc.cli.blockchain.blockchainCommands
import net.postchain.mc.cli.cluster.*
import net.postchain.mc.cli.container.containerCommands
import net.postchain.mc.cli.proposal.proposalCommands
import net.postchain.mc.cli.container.replica.containerReplicaCommands
import net.postchain.mc.cli.node.nodeCommands
import net.postchain.mc.cli.provider.providerCommands

class ManagementConsole : CliktCommand(name = "postchain-mc") {
    override fun run() = Unit
}

fun main(args: Array<String>) = ManagementConsole()
    .subcommands(
        CommandKeygen(),

        // Init
        CommandInit(),

        // Voting
        proposalCommands(),

        // Blockchain
        blockchainCommands(),

        // Cluster
        clusterCommands(),

        // Provider
        providerCommands(),

        // Container
        containerCommands(),

        // Node
        nodeCommands(),
        )
    .main(args)
