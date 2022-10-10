package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandAddNode : CliktCommand(
    name = "add",
    help = "Add or update node information"
) {
    private val config by lazy { read() }

    private val key by requiredPubkeyOption()

    private val host by hostOption().required()

    private val port by portOption().required()

    private val apiUrl by option("-a", "--api-url", help = "api url").required()

    private val clusterName by option(
        "-c",
        "--cluster",
        help = "comma delimited list of clusters this node belongs to"
    ).required()

    override fun run() {
        CliExecution(config).addNode(key, host, port.toLong(), apiUrl, clusterName)
        println("Node has been added successfully")
    }
}
