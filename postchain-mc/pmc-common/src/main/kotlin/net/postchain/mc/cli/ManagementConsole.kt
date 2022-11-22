package net.postchain.mc.cli

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import net.postchain.cli.CommandKeygen
import net.postchain.mc.cli.blockchain.blockchainCommands
import net.postchain.mc.cli.cluster.clusterCommands
import net.postchain.mc.cli.container.containerCommands
import net.postchain.mc.cli.node.nodeCommands
import net.postchain.mc.cli.proposal.proposalCommands
import net.postchain.mc.cli.provider.providerCommands
import net.postchain.mc.cli.votingupdates.voterSetCommands
import net.postchain.mc.network.networkCommands

class ManagementConsole : NoOpCliktCommand(name = "postchain-mc") {

    init {
        versionOption(this::class.java.`package`.implementationVersion)
        subcommands(
                CommandKeygen(),
                networkCommands(),
                proposalCommands(),
                voterSetCommands(),
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
    }

    override fun aliases(): Map<String, List<String>> {
        return mapOf(
                "init" to listOf("network", "initialize"),
                "initialize" to listOf("network", "initialize"),
                "blockchains" to listOf("blockchain", "list"),
                "bcs" to listOf("blockchain", "list"),
                "votersets" to listOf("voterset", "list"),
                "containers" to listOf("container", "list"),
                "clusters" to listOf("cluster", "list"),
                "providers" to listOf("provider", "list"),
                "proposals" to listOf("proposal", "list")
        )
    }
}
