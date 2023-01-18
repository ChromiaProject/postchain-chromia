package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.configOption

class CommandProposeClusterProvider : CliktCommand(
        name = "provider",
        help = "proposes an update of a cluster's providers. add = false => remove provider from cluster. Cluster governance voter set has authority to update a cluster's providers"
) {
    private val config by configOption()

    private val provider by requiredPubkeyOption()

    private val clusterName by option(
            "-c", "--cluster",
            help = "Name of existing cluster to update"
    ).required()

    private val add by option("-a", "--add", help = "Add or remove provider pubkey from cluster")
            .flag("-r", "--remove", default = true)

    override fun run() {
        CliExecution(config).proposeClusterProvider(clusterName, provider.hex(), add)
    }
}