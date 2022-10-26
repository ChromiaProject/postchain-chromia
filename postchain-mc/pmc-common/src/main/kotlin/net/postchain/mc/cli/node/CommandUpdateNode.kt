package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import net.postchain.chain0.common.cluster.addNodeToClusterOperation
import net.postchain.chain0.common.updateNodeApiUrlOperation
import net.postchain.chain0.common.updateNodeHostOperation
import net.postchain.chain0.common.updateNodePortOperation
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.mc.cli.base.ClientUtil
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
        val pubkey = key.hexStringToWrappedByteArray()
        val provider = client.config.pubkey().wData
        val builder = client.transactionBuilder()
        host?.let { builder.updateNodeHostOperation(provider, pubkey, it) }
        port?.let { builder.updateNodePortOperation(provider, pubkey, it.toLong()) }
        apiUrl?.let { builder.updateNodeApiUrlOperation(provider, pubkey, it) }
        clusterName?.forEach { builder.addNodeToClusterOperation(provider, pubkey, it) }
        builder.postSyncAwaitConfirmation()
                .printResult("Node information was updated", "Node information update failed")
    }
}
