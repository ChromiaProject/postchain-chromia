package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOrGenerateOption

class CommandAddCluster : CliktCommand(
        name = "add",
        help = "Create a new cluster that can hold containers with blockchains."
) {

    private val config by configOption()

    private val name by nameOrGenerateOption("Cluster name")

    private val providers by option(
            "-p", "--providers",
            help = "String of comma separated list of pubkey strings of providers that should belong to this cluster"
    ).required()

    private val governorName by option(
            "-g", "--governor",
            help = "Name of another voter set which can update this cluster."
    ).required()

    private val deployerName by option(
            "-d", "--deployer",
            help = "Name of the voter set which can make updates in this cluster"
    ).required()

    override fun run() {
        CliExecution(config).addCluster(
                name,
                providers,
                governorName,
                deployerName
        )
    }
}