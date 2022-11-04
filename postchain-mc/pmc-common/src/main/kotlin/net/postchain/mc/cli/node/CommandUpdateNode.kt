package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import net.postchain.chain0.common.cluster.addNodeToClusterOperation
import net.postchain.chain0.common.updateNodeOperation
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandUpdateNode : CliktCommand(
        name = "update",
        help = "Update node information"
) {
    private val client by nopClientOption()

    private val key by requiredPubkeyOption()

    private val host by hostOption()

    private val port by portOption()

    private val apiUrl by option("-a", "--api-url", help = "api url")

    private val clusterName by option(
            "-c",
            "--cluster",
            help = "comma delimited list of clusters to add this node to"
    ).split(",")

    override fun run() {
        if (host == null && port == null && apiUrl == null && clusterName == null) {
            println("No properties to update. At least one node's property should be specified")
            return
        }

        val provider = client.config.pubkey().data
        val pubkey = key.hexStringToByteArray()
        client.transactionBuilder()
                .apply {
                    if (host != null || port != null || apiUrl != null) {
                        updateNodeOperation(provider, pubkey, host, port?.toLong(), apiUrl)
                    }
                }
                .apply {
                    clusterName?.forEach { addNodeToClusterOperation(provider, pubkey, it) }
                }
                .postSyncAwaitConfirmation()
                .printResult("Node information was updated", "Node information update failed")
    }
}
