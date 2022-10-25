package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.cluster.cluster_op.createClusterFromOperation
import net.postchain.chain0.cluster.cluster_op.createClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.container.DeployerOption
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.cli.util.nopClientOption

class CommandAddCluster : CliktCommand(
    name = "add",
    help = "Create a new cluster that can hold containers with blockchains."
) {

    private val client by nopClientOption()

    private val name by nameOrGenerateOption("Cluster name")

    private val providerOptions by mutuallyExclusiveOptions(
        option("--voter-set").convert { DeployerOption.VoterSet(it) },
        option("--deployers").convert { DeployerOption.Deployer(it) }
    ).single().required()

    private val governorName by option(
        "-g", "--governor",
        help = "Name of another voter set which can update this cluster."
    ).required()

    override fun run() {
        client.transactionBuilder()
            .apply {
                when (providerOptions) {
                    is DeployerOption.Deployer -> createClusterOperation(client.pubkey, name, governorName, (providerOptions as DeployerOption.Deployer).pubkeys)
                    is DeployerOption.VoterSet -> createClusterFromOperation(client.pubkey, name, governorName, (providerOptions as DeployerOption.VoterSet).data)
                }
            }
            .postSyncAwaitConfirmation()
            .printResult(
                "Cluster $name added",
                "Could not create cluster"
            )
    }
}

