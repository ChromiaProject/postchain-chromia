package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import net.postchain.chain0.common.registerNodeOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.hostOption
import net.postchain.mc.cli.portOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption

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

    override fun run() {
        client.transactionBuilder()
                .registerNodeOperation(client.pubkey, key.data, host, port.toLong(), apiUrl, clusters)
                .postAwaitConfirmation()
                .printResult(
                        "Node registered",
                        "Failed to register node"
                )
    }
}
