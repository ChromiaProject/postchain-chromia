package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.cluster.cluster_op.requestClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.cli.util.nopClientOption

class CommandRequestCluster : CliktCommand(
        name = "request",
        help = "Request system creating a new cluster"
) {

    private val client by nopClientOption()

    private val name by nameOrGenerateOption("Cluster name")

    private val size by option(help = "Size of cluster to be created").long().required()

    private val requireFull by option(help = "Fail if cluster is not full").flag("--do-not-require-full", default = true)

    override fun run() {
        client.transactionBuilder()
                .requestClusterOperation(client.pubkey, name, size, requireFull)
                .postAwaitConfirmation()
                .printResult(
                        "Cluster $name was created",
                        "Could not create cluster"
                )
    }
}

