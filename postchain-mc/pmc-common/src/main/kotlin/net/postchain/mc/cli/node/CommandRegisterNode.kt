package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import net.postchain.chain0.common.operations.registerNodeOperation
import net.postchain.chain0.common.operations.updateNodeCapabilityOperation
import net.postchain.chain0.model.NodeCapabilityType
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.hostOption
import net.postchain.mc.cli.portOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption
import net.postchain.mc.network.NodeVerifier

class CommandRegisterNode : CliktCommand(
        name = "register",
        help = "Registers a node"
) {
    private val client by nopClientOption()

    private val key by pubkeyOption("Node pubkey").required()

    private val host by hostOption().required()

    private val port by portOption().required()

    private val apiUrl by option("-a", "--api-url", help = "api url").required()

    private val clusters by option(
            "-c",
            "--cluster",
            help = "comma delimited list of clusters this node belongs to"
    ).split(",").default(emptyList())

    private val capability by option(help = "Node capability").enum<NodeCapabilityType>().multiple()
    override fun run() {
        val verifier = NodeVerifier(client.config)
        if (!verifier.verifyApi(apiUrl).first) throw CliktError("Api url is not accessible for host")
        if (!verifier.verifyHost(host, port)) throw CliktError("Node is not accessible")
        client.transactionBuilder()
                .registerNodeOperation(client.pubkey, key.data, host, port.toLong(), apiUrl, clusters)
                .apply {
                    if (capability.isNotEmpty()) capability.forEach { updateNodeCapabilityOperation(client.pubkey, key.data, it, true) }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Node registered",
                        "Failed to register node"
                )
    }
}
