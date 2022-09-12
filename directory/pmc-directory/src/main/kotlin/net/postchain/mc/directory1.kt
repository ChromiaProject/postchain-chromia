package net.postchain.mc

import com.github.ajalt.clikt.core.subcommands
import net.postchain.cli.*
import net.postchain.mc.cli.ManagementConsole
import net.postchain.mc.cli.blockchain.blockchainCommands
import net.postchain.mc.cli.cluster.*
import net.postchain.mc.cli.container.containerCommands
import net.postchain.mc.cli.proposal.proposalCommands
import net.postchain.mc.cli.node.nodeCommands
import net.postchain.mc.cli.provider.providerCommands
import net.postchain.mc.cli.votingupdates.voterSetCommands
import net.postchain.mc.network.networkCommands

fun main(args: Array<String>) = ManagementConsole()
    .subcommands(
        CommandKeygen(),

        networkCommands(),

        // Voting
        proposalCommands(),
        voterSetCommands(),

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
