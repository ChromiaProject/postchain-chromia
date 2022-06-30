package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandProposeClusterProvider : CliktCommand(
    name = "provider",
    help = "proposes an update of a cluster's providers. add = false => remove provider from cluster. Cluster governance voter set has authority to update a cluster's providers"
) {
    private val nodeConfig by nodeConfigOption()

    private val provider by requiredPubkeyOption()


    private val clusterName by option(
        "-c", "--cluster",
        help = "Name of existing cluster to update"
    ).required()

    private val add by option("-a", "--add", help = "Add or remove provider pubkey from cluster")
        .flag("-r", "--remove", default = true)

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).proposeClusterProvider(clusterName, provider, add)
        println("proposal for provider update of cluster $clusterName has been added successfully")
    }
}