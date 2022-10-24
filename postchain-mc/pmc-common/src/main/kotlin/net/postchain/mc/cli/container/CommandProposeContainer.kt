package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.*
import net.postchain.chain0.container.container_op.createContainerFromOperation
import net.postchain.chain0.container.container_op.createContainerOperation
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.cli.util.nopClientOption

sealed class DeployerOption {
    class Deployer(val data: ByteArray): DeployerOption()
    class VoterSet(val data: String): DeployerOption()

}

class CommandProposeContainer : CliktCommand(
    name = "add",
    help = "propose a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it."
) {
    private val client by nopClientOption()

    private val name by nameOrGenerateOption("Container name")

    private val clusterName by option(
        "-c", "--cluster",
        help = "Name of cluster to put container in. Must exist in database"
    ).required()

    private val consensus by option().flag()

    private val deployerOption by mutuallyExclusiveOptions(
        option("--voter-set").convert { DeployerOption.VoterSet(it) },
        option("--deployers").convert { DeployerOption.Deployer(it.hexStringToByteArray()) }
    ).single().required()

    override fun run() {
        val threshold = if (consensus) -1L else 1L
        client.transactionBuilder()
            .apply {
                when (deployerOption) {
                    is DeployerOption.Deployer -> createContainerOperation(client.config.pubkey().data, name, clusterName, threshold, listOf(
                        (deployerOption as DeployerOption.Deployer).data))
                    is DeployerOption.VoterSet -> createContainerFromOperation(client.config.pubkey().data, name, clusterName, threshold, (deployerOption as DeployerOption.VoterSet).data)
                }

            }
            .postSyncAwaitConfirmation()
            .printResult("Container has been created",
            "Failed to create container")
    }
}
