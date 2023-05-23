package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.updateNodeCapabilityOperation
import net.postchain.chain0.common.operations.updateNodeOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.model.NodeCapabilityType
import net.postchain.chain0.version.apiVersion
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.hostOption
import net.postchain.mc.cli.portOption
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.clusterUnitsOption
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

    private val clusterUnits by clusterUnitsOption()

    private val clusterName by option(
            "-c",
            "--cluster",
            help = "comma delimited list of clusters to add this node to"
    ).split(",")

    private val addCapability by option(help = "Node capability").enum<NodeCapabilityType>()
    private val removeCapability by option(help = "Node capability").enum<NodeCapabilityType>()

    override fun run() {
        if (host == null && port == null && apiUrl == null && clusterName == null && addCapability == null && removeCapability == null) {
            echo("No properties to update. At least one node's property should be specified")
            return
        }

        val apiVersion = client.apiVersion()
        val provider = client.config.pubkey().data
        client.transactionBuilder()
                .apply {
                    when {
                        apiVersion >= 3 -> {
                            if (host != null || port != null || apiUrl != null || clusterUnits != null) {
                                updateNodeWithUnitsOperation(provider, key.data, host, port?.toLong(), apiUrl, clusterUnits)
                            }
                        }

                        else -> {
                            if (host != null || port != null || apiUrl != null) {
                                updateNodeOperation(provider, key.data, host, port?.toLong(), apiUrl)
                            }
                        }
                    }
                    clusterName?.forEach { addNodeToClusterOperation(provider, key.data, it) }
                    addCapability?.let { updateNodeCapabilityOperation(provider, key.data, it, true) }
                    removeCapability?.let { updateNodeCapabilityOperation(provider, key.data, it, false) }
                }
                .postAwaitConfirmation()
                .printResult("Node information was updated", "Node information update failed")
    }
}
