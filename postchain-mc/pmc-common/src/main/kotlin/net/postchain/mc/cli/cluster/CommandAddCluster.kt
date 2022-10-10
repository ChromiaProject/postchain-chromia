package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import net.postchain.chain0.common.createClusterOperation
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOrGenerateOption

class CommandAddCluster : CliktCommand(
        name = "add",
        help = "Create a new cluster that can hold containers with blockchains."
) {

    private val config by lazy { read() }

    private val name by nameOrGenerateOption("Cluster name")

    private val providers by option(
            "-p", "--providers",
            help = "String of comma separated list of pubkey strings of providers that should belong to this cluster"
    ).convert { it.hexStringToByteArray() }.split(",").required()

    private val governorName by option(
            "-g", "--governor",
            help = "Name of another voter set which can update this cluster."
    ).required()

    private val deployerName by option(
            "-d", "--deployer",
            help = "Name of the voter set which can make updates in this cluster"
    ).required()

    override fun run() {
        val client = ClientUtil.nopClientFromConfig(config)
        client.transactionBuilder()
                .createClusterOperation(config.pubkey().key, name, providers, governorName, deployerName)
                .postSyncAwaitConfirmation()
                .printResult(
                        "Cluster $name added",
                        "Could not create cluster"
                )
    }
}
